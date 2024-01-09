/*
 * Copyright (c) 2023 OceanBase.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.oceanbase.odc.service.task.caller;

import com.oceanbase.odc.service.task.config.JobConfiguration;
import com.oceanbase.odc.service.task.config.JobConfigurationHolder;
import com.oceanbase.odc.service.task.enums.JobCallerAction;
import com.oceanbase.odc.service.task.listener.JobCallerEvent;
import com.oceanbase.odc.service.task.schedule.ExecutorIdentifier;
import com.oceanbase.odc.service.task.schedule.JobIdentity;

import lombok.extern.slf4j.Slf4j;

/**
 * @author yaobin
 * @date 2023-11-16
 * @since 4.2.4
 */
@Slf4j
public abstract class BaseJobCaller implements JobCaller {

    @Override
    public void start(JobContext context) throws JobException {
        try {
            ExecutorIdentifier executorIdentifier = tryStart(context);
            publishEvent(new JobCallerEvent(context.getJobIdentity(), JobCallerAction.START, true,
                    executorIdentifier, null));
        } catch (Exception ex) {
            publishEvent(new JobCallerEvent(context.getJobIdentity(), JobCallerAction.START, false, ex));
            throw new JobException(ex);
        }
    }

    private ExecutorIdentifier tryStart(JobContext context) throws Exception {
        try {
            return doStart(context);
        } catch (Exception e) {
            log.warn("Start job {} failed and retry again, error is: ", context.getJobIdentity().getId(), e);
            throw e;
        }
    }

    @Override
    public void stop(JobIdentity ji) throws JobException {
        try {
            doStop(ji);
            publishEvent(new JobCallerEvent(ji, JobCallerAction.STOP, true, null));
        } catch (JobException ex) {
            publishEvent(new JobCallerEvent(ji, JobCallerAction.STOP, false, ex));
            throw ex;
        }
    }

    protected abstract ExecutorIdentifier doStart(JobContext context) throws JobException;

    protected abstract void doStop(JobIdentity ji) throws JobException;

    private void publishEvent(JobCallerEvent event) {
        JobConfiguration configuration = JobConfigurationHolder.getJobConfiguration();
        if (configuration != null && configuration.getEventPublisher() != null) {
            configuration.getEventPublisher().publishEvent(event);
        }
    }

}
