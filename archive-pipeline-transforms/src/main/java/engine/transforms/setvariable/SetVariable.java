/*! ******************************************************************************
 *
 * Hop : The Hop Orchestration Platform
 *
 * http://www.project-hop.org
 *
 *******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package org.apache.hop.pipeline.transforms.setvariable;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.exception.HopTransformException;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.iVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.workflow.Job;
import org.apache.hop.pipeline.Pipeline;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.BaseTransform;
import org.apache.hop.pipeline.transform.ITransformData;
import org.apache.hop.pipeline.transform.ITransform;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * Convert Values in a certain fields to other values
 *
 * @author Matt
 * @since 27-apr-2006
 */
public class SetVariable extends BaseTransform implements ITransform {
  private static Class<?> PKG = SetVariableMeta.class; // for i18n purposes, needed by Translator!!

  private SetVariableMeta meta;
  private SetVariableData data;

  public SetVariable( TransformMeta transformMeta, ITransformData data, int copyNr, PipelineMeta pipelineMeta,
                      Pipeline pipeline ) {
    super( transformMeta, meta, data, copyNr, pipelineMeta, pipeline );
  }

  public boolean processRow() throws HopException {
    meta = (SetVariableMeta) smi;
    data = (SetVariableData) sdi;

    // Get one row from one of the rowsets...
    //
    Object[] rowData = getRow();
    if ( rowData == null ) { // means: no more input to be expected...

      if ( first ) {
        // We do not received any row !!
        logBasic( BaseMessages.getString( PKG, "SetVariable.Log.NoInputRowSetDefault" ) );
        for ( int i = 0; i < meta.getFieldName().length; i++ ) {
          if ( !Utils.isEmpty( meta.getDefaultValue()[ i ] ) ) {
            setValue( rowData, i, true );
          }
        }
      }

      logBasic( "Finished after " + getLinesWritten() + " rows." );
      setOutputDone();
      return false;
    }

    if ( first ) {
      first = false;

      data.outputMeta = getInputRowMeta().clone();

      logBasic( BaseMessages.getString( PKG, "SetVariable.Log.SettingVar" ) );

      for ( int i = 0; i < meta.getFieldName().length; i++ ) {
        setValue( rowData, i, false );
      }

      putRow( data.outputMeta, rowData );
      return true;
    }

    throw new HopTransformException( BaseMessages.getString(
      PKG, "SetVariable.RuntimeError.MoreThanOneRowReceived.SETVARIABLE0007" ) );
  }

  private void setValue( Object[] rowData, int i, boolean usedefault ) throws HopException {
    // Set the appropriate environment variable
    //
    String value = null;
    if ( usedefault ) {
      value = environmentSubstitute( meta.getDefaultValue()[ i ] );
    } else {
      int index = data.outputMeta.indexOfValue( meta.getFieldName()[ i ] );
      if ( index < 0 ) {
        throw new HopException( "Unable to find field [" + meta.getFieldName()[ i ] + "] in input row" );
      }
      IValueMeta valueMeta = data.outputMeta.getValueMeta( index );
      Object valueData = rowData[ index ];

      // Get variable value
      //
      if ( meta.isUsingFormatting() ) {
        value = valueMeta.getString( valueData );
      } else {
        value = valueMeta.getCompatibleString( valueData );
      }

    }

    if ( value == null ) {
      value = "";
    }

    // Get variable name
    String varname = meta.getVariableName()[ i ];

    if ( Utils.isEmpty( varname ) ) {
      if ( Utils.isEmpty( value ) ) {
        throw new HopException( "Variable name nor value was specified on line #" + ( i + 1 ) );
      } else {
        throw new HopException( "There was no variable name specified for value [" + value + "]" );
      }
    }

    Job parentJob = null;

    // We always set the variable in this transform and in the parent pipeline...
    //
    setVariable( varname, value );

    // Set variable in the pipeline
    //
    Pipeline pipeline = getPipeline();
    pipeline.setVariable( varname, value );

    // Make a link between the pipeline and the parent pipeline (in a sub-pipeline)
    //
    while ( pipeline.getParentPipeline() != null ) {
      pipeline = pipeline.getParentPipeline();
      pipeline.setVariable( varname, value );
    }

    // The pipeline object we have now is the pipeline being executed by a workflow.
    // It has one or more parent workflows.
    // Below we see where we need to this value as well...
    //
    switch ( meta.getVariableType()[ i ] ) {
      case SetVariableMeta.VARIABLE_TYPE_JVM:

        System.setProperty( varname, value );

        parentJob = pipeline.getParentJob();
        while ( parentJob != null ) {
          parentJob.setVariable( varname, value );
          parentJob = parentJob.getParentJob();
        }

        break;
      case SetVariableMeta.VARIABLE_TYPE_ROOT_WORKFLOW:
        // Comments by SB
        // iVariables rootJob = null;
        parentJob = pipeline.getParentJob();
        while ( parentJob != null ) {
          parentJob.setVariable( varname, value );
          // rootJob = parentJob;
          parentJob = parentJob.getParentJob();
        }
        break;

      case SetVariableMeta.VARIABLE_TYPE_GRAND_PARENT_WORKFLOW:
        // Set the variable in the parent workflow
        //
        parentJob = pipeline.getParentJob();
        if ( parentJob != null ) {
          parentJob.setVariable( varname, value );
        } else {
          throw new HopTransformException( "Can't set variable ["
            + varname + "] on parent workflow: the parent workflow is not available" );
        }

        // Set the variable on the grand-parent workflow
        //
        iVariables gpJob = pipeline.getParentJob().getParentJob();
        if ( gpJob != null ) {
          gpJob.setVariable( varname, value );
        } else {
          throw new HopTransformException( "Can't set variable ["
            + varname + "] on grand parent workflow: the grand parent workflow is not available" );
        }
        break;

      case SetVariableMeta.VARIABLE_TYPE_PARENT_WORKFLOW:
        // Set the variable in the parent workflow
        //
        parentJob = pipeline.getParentJob();
        if ( parentJob != null ) {
          parentJob.setVariable( varname, value );
        } else {
          throw new HopTransformException( "Can't set variable ["
            + varname + "] on parent workflow: the parent workflow is not available" );
        }
        break;

      default:
        break;
    }

    logBasic( BaseMessages.getString( PKG, "SetVariable.Log.SetVariableToValue", meta.getVariableName()[ i ], value ) );
  }

  public void.dispose() {
    meta = (SetVariableMeta) smi;
    data = (SetVariableData) sdi;

    super.dispose();
  }

  public boolean init() {
    meta = (SetVariableMeta) smi;
    data = (SetVariableData) sdi;

    if ( super.init() ) {
      return true;
    }
    return false;
  }

}
