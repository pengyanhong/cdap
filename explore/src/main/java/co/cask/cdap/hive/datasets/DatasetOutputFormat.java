/*
 * Copyright 2014 Cask, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.hive.datasets;

import co.cask.cdap.api.data.batch.RecordWritable;
import co.cask.cdap.common.conf.Constants;
import com.continuuity.tephra.TransactionAware;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.OutputFormat;
import org.apache.hadoop.mapred.RecordWriter;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.util.Progressable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class DatasetOutputFormat implements OutputFormat<Void, ObjectWritable> {

  public static final Map<String, List<byte[]>> QUERY_TO_CHANGES = Maps.newHashMap();

  @Override
  public RecordWriter<Void, ObjectWritable> getRecordWriter(FileSystem ignored, final JobConf jobConf, String name,
                                                            Progressable progress) throws IOException {
    final RecordWritable recordWritable = DatasetAccessor.getRecordWritable(jobConf);
    return new RecordWriter<Void, ObjectWritable>() {
      @Override
      public void write(Void key, ObjectWritable value) throws IOException {
        recordWritable.write(value.get());
      }

      @Override
      public void close(Reporter reporter) throws IOException {
        // TODO save the tx changes here. How to get them from the MR job to the CLI service though?...
        // If we add them to the jobconf, is it gonna be send back? Don't think so
        // TODO check entries contains this tx entry
        if (recordWritable instanceof RecordWritable) {
          String queryId = jobConf.get(Constants.Explore.QUERY_ID);
          List<byte[]> changes = QUERY_TO_CHANGES.get(queryId);
          if (changes == null) {
            changes = Lists.newArrayList();
          }
          changes.addAll(((TransactionAware) recordWritable).getTxChanges());

          QUERY_TO_CHANGES.put(queryId, changes);
        }
      }
    };
  }

  @Override
  public void checkOutputSpecs(FileSystem ignored, JobConf job) throws IOException {
    // TODO what should be done here?
  }
}
