/*
 * Copyright 2015-2017 Hewlett Packard Enterprise Development LP.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hpe.caf.worker.core;

import com.hpe.caf.api.Codec;
import com.hpe.caf.api.CodecException;
import com.hpe.caf.api.ConfigurationException;
import com.hpe.caf.api.ConfigurationSource;
import com.hpe.caf.api.HealthResult;
import com.hpe.caf.api.worker.*;
import com.hpe.caf.codec.JsonCodec;
import com.hpe.caf.naming.ServicePath;
import com.hpe.caf.worker.AbstractWorker;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.mockito.Mockito;
import org.testng.internal.junit.ArrayAsserts;

import javax.naming.InvalidNameException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class WorkerCoreTest
{
    private static final String SUCCESS = "success";
    private static final String WORKER_NAME = "testWorker";
    private static final int WORKER_API_VER = 1;
    private static final String QUEUE_MSG_ID = "test1";
    private static final String QUEUE_IN = "inQueue";
    private static final String QUEUE_OUT = "outQueue";
    private static final String SERVICE_PATH = "/test/group";


    /** Send a message all the way through WorkerCore and verify the result output message **/
    @Test
    public void testWorkerCore()
        throws CodecException, InterruptedException, WorkerException, ConfigurationException, QueueException, InvalidNameException
    {
        BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        Codec codec = new JsonCodec();
        WorkerThreadPool wtp = WorkerThreadPool.create(5);
        ConfigurationSource config = Mockito.mock(ConfigurationSource.class);
        ServicePath path = new ServicePath(SERVICE_PATH);
        TestWorkerTask task = new TestWorkerTask();
        TestWorkerQueue queue = new TestWorkerQueueProvider(q).getWorkerQueue(config, 50);
        WorkerCore core = new WorkerCore(codec, wtp, queue, getWorkerFactory(task, codec), path);
        core.start();
        // at this point, the queue should hand off the task to the app, the app should get a worker from the mocked WorkerFactory,
        // and the Worker itself is a mock wrapped in a WorkerWrapper, which should return success and the appropriate result data
        byte[] stuff = codec.serialise(getTaskMessage(task, codec, WORKER_NAME));
        queue.submitTask(QUEUE_MSG_ID, stuff);
        // the worker's task result should eventually be passed back to our dummy WorkerQueue and onto our blocking queue
        byte[] result = q.poll(5000, TimeUnit.MILLISECONDS);
        // if the result didn't get back to us, then result will be null
        Assert.assertNotNull(result);
        // deserialise and verify result data
        TaskMessage taskMessage = codec.deserialise(result, TaskMessage.class);
        Assert.assertEquals(TaskStatus.RESULT_SUCCESS, taskMessage.getTaskStatus());
        Assert.assertEquals(WORKER_NAME, taskMessage.getTaskClassifier());
        Assert.assertEquals(WORKER_API_VER, taskMessage.getTaskApiVersion());
        TestWorkerResult workerResult = codec.deserialise(taskMessage.getTaskData(), TestWorkerResult.class);
        Assert.assertEquals(SUCCESS, workerResult.getResultString());
        Assert.assertTrue(taskMessage.getContext().containsKey(path.toString()));
        ArrayAsserts.assertArrayEquals(SUCCESS.getBytes(StandardCharsets.UTF_8), taskMessage.getContext().get(path.toString()));
    }


    /** Send a message with tracking info **/
    @Test
    public void testWorkerCoreWithTracking()
            throws CodecException, InterruptedException, WorkerException, ConfigurationException, QueueException, InvalidNameException
    {
        BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        Codec codec = new JsonCodec();
        WorkerThreadPool wtp = WorkerThreadPool.create(5);
        ConfigurationSource config = Mockito.mock(ConfigurationSource.class);
        ServicePath path = new ServicePath(SERVICE_PATH);
        TestWorkerTask task = new TestWorkerTask();
        TestWorkerQueue queue = new TestWorkerQueueProvider(q).getWorkerQueue(config, 50);
        WorkerCore core = new WorkerCore(codec, wtp, queue, getWorkerFactory(task, codec), path);
        core.start();
        // at this point, the queue should hand off the task to the app, the app should get a worker from the mocked WorkerFactory,
        // and the Worker itself is a mock wrapped in a WorkerWrapper, which should return success and the appropriate result data
        TrackingInfo tracking = new TrackingInfo("J23.1.2", new Date(), "http://thehost:1234/job-service/v1/jobs/23/isActive", "trackingQueue", "trackTo");
        byte[] stuff = codec.serialise(getTaskMessage(task, codec, WORKER_NAME, tracking));
        queue.submitTask(QUEUE_MSG_ID, stuff);
        // the worker's task result should eventually be passed back to our dummy WorkerQueue and onto our blocking queue
        byte[] result = q.poll(5000, TimeUnit.MILLISECONDS);
        // if the result didn't get back to us, then result will be null
        Assert.assertNotNull(result);
        // deserialise and verify result data
        TaskMessage taskMessage = codec.deserialise(result, TaskMessage.class);
        Assert.assertEquals(TaskStatus.RESULT_SUCCESS, taskMessage.getTaskStatus());
        Assert.assertEquals(WORKER_NAME, taskMessage.getTaskClassifier());
        Assert.assertEquals(WORKER_API_VER, taskMessage.getTaskApiVersion());
        TestWorkerResult workerResult = codec.deserialise(taskMessage.getTaskData(), TestWorkerResult.class);
        Assert.assertEquals(SUCCESS, workerResult.getResultString());
        Assert.assertTrue(taskMessage.getContext().containsKey(path.toString()));
        ArrayAsserts.assertArrayEquals(SUCCESS.getBytes(StandardCharsets.UTF_8), taskMessage.getContext().get(path.toString()));
    }


    /** Test WorkerCore when the input message doesn't even decode to a TaskWrapper - should be an InvalidTaskException **/
    @Test(expectedExceptions = InvalidTaskException.class)
    public void testInvalidWrapper()
        throws InvalidNameException, WorkerException, QueueException, CodecException
    {
        BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        Codec codec = new JsonCodec();
        WorkerThreadPool wtp = WorkerThreadPool.create(5);
        ConfigurationSource config = Mockito.mock(ConfigurationSource.class);
        ServicePath path = new ServicePath(SERVICE_PATH);
        TestWorkerTask task = new TestWorkerTask();
        TestWorkerQueue queue = new TestWorkerQueueProvider(q).getWorkerQueue(config, 50);
        WorkerCore core = new WorkerCore(codec, wtp, queue, getWorkerFactory(task, codec), path);
        core.start();
        byte[] stuff = codec.serialise("nonsense");
        queue.submitTask(QUEUE_MSG_ID, stuff);
    }


    /** Send in a TaskMessage put with an inner task-specific message that cannot be decoded, this should be an INVALID_TASK response **/
    @Test
    public void testInvalidTask()
        throws QueueException, InvalidNameException, WorkerException, CodecException, InterruptedException
    {
        BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        Codec codec = new JsonCodec();
        WorkerThreadPool wtp = WorkerThreadPool.create(5);
        ConfigurationSource config = Mockito.mock(ConfigurationSource.class);
        ServicePath path = new ServicePath(SERVICE_PATH);
        TestWorkerTask task = new TestWorkerTask();
        TestWorkerQueue queue = new TestWorkerQueueProvider(q).getWorkerQueue(config, 50);
        WorkerCore core = new WorkerCore(codec, wtp, queue, getInvalidTaskWorkerFactory(), path);
        core.start();
        TaskMessage tm = getTaskMessage(task, codec, WORKER_NAME);
        tm.setTaskData(codec.serialise("invalid task data"));
        Map<String, byte[]> context = new HashMap<>();
        String testContext = "test";
        byte[] testContextData = testContext.getBytes(StandardCharsets.UTF_8);
        context.put(testContext, testContextData);
        tm.setContext(context);
        byte[] stuff = codec.serialise(tm);
        queue.submitTask(QUEUE_MSG_ID, stuff);
        byte[] result = q.poll(5000, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(result);
        TaskMessage taskMessage = codec.deserialise(result, TaskMessage.class);
        Assert.assertEquals(TaskStatus.INVALID_TASK, taskMessage.getTaskStatus());
        Assert.assertEquals(WORKER_NAME, taskMessage.getTaskClassifier());
        Assert.assertEquals(WORKER_API_VER, taskMessage.getTaskApiVersion());
        Assert.assertTrue(taskMessage.getContext().containsKey(testContext));
        ArrayAsserts.assertArrayEquals(testContextData, taskMessage.getContext().get(testContext));
        Assert.assertEquals(QUEUE_OUT, queue.getLastQueue());
    }


    /** Send three tasks into a WorkerCore with only two threads, abort them all, check the running ones are interrupted and the other one never starts **/
    @Test
    public void testAbortTasks()
        throws CodecException, InterruptedException, WorkerException, ConfigurationException, QueueException, InvalidNameException
    {
        BlockingQueue<byte[]> q = new LinkedBlockingQueue<>();
        Codec codec = new JsonCodec();
        WorkerThreadPool wtp = WorkerThreadPool.create(2);
        ConfigurationSource config = Mockito.mock(ConfigurationSource.class);
        ServicePath path = new ServicePath(SERVICE_PATH);
        TestWorkerTask task = new TestWorkerTask();
        CountDownLatch latch = new CountDownLatch(2);
        TestWorkerQueue queue = new TestWorkerQueueProvider(q).getWorkerQueue(config, 20);
        WorkerCore core = new WorkerCore(codec, wtp, queue, getSlowWorkerFactory(latch, task, codec), path);
        core.start();
        byte[] task1 = codec.serialise(getTaskMessage(task, codec, UUID.randomUUID().toString()));
        byte[] task2 = codec.serialise(getTaskMessage(task, codec, UUID.randomUUID().toString()));
        byte[] task3 = codec.serialise(getTaskMessage(task, codec, UUID.randomUUID().toString()));
        queue.submitTask("task1", task1);
        queue.submitTask("task2", task2);
        queue.submitTask("task3", task3);   // there are only 2 threads, so this task should not even start
        Thread.sleep(500);  // give the test a little breathing room
        queue.triggerAbort();
        latch.await(1, TimeUnit.SECONDS);
        Thread.sleep(100);
        Assert.assertEquals(3, core.getStats().getTasksReceived());
        Assert.assertEquals(3, core.getStats().getTasksAborted());
        Assert.assertEquals(0, core.getStats().getTasksForwarded());
        Assert.assertEquals(0, core.getStats().getTasksDiscarded());
        Assert.assertEquals(0, core.getBacklogSize());
    }


    private TaskMessage getTaskMessage(final TestWorkerTask task, final Codec codec, final String taskId)
        throws CodecException
    {
        return getTaskMessage(task, codec, taskId, null);
    }


    private TaskMessage getTaskMessage(final TestWorkerTask task, final Codec codec, final String taskId, final TrackingInfo tracking)
            throws CodecException
    {
        TaskMessage tm = new TaskMessage();
        tm.setTaskId(taskId);
        tm.setTaskStatus(TaskStatus.NEW_TASK);
        tm.setTaskClassifier(WORKER_NAME);
        tm.setTaskApiVersion(WORKER_API_VER);
        tm.setTaskData(codec.serialise(task));
        tm.setTo(QUEUE_IN);
        tm.setTracking(tracking);
        return tm;
    }


    private WorkerFactory getWorkerFactory(final TestWorkerTask task, final Codec codec)
            throws WorkerException
    {
        WorkerFactory factory = Mockito.mock(WorkerFactory.class);
        Worker mockWorker = getWorker(task, codec);
        Mockito.when(factory.getWorker(Mockito.any())).thenReturn(mockWorker);
        return factory;
    }

    private WorkerFactory getInvalidTaskWorkerFactory()
        throws WorkerException
    {
        WorkerFactory factory = Mockito.mock(WorkerFactory.class);
        Mockito.when(factory.getWorker(Mockito.any())).thenThrow(InvalidTaskException.class);
        Mockito.when(factory.getInvalidTaskQueue()).thenReturn(QUEUE_OUT);
        return factory;
    }


    private WorkerFactory getSlowWorkerFactory(final CountDownLatch latch, final TestWorkerTask task, final Codec codec)
        throws WorkerException
    {
        WorkerFactory factory = Mockito.mock(WorkerFactory.class);
        Worker mockWorker = new SlowWorker(task, QUEUE_OUT, codec, latch);
        Mockito.when(factory.getWorker(Mockito.any())).thenReturn(mockWorker);
        return factory;
    }


    private Worker getWorker(final TestWorkerTask task, final Codec codec)
        throws InvalidTaskException
    {
        return new AbstractWorker<TestWorkerTask, TestWorkerResult>(task, QUEUE_OUT, codec)
        {
            @Override
            public WorkerResponse doWork()
            {
                TestWorkerResult result = new TestWorkerResult();
                result.setResultString(SUCCESS);
                return createSuccessResult(result, SUCCESS.getBytes(StandardCharsets.UTF_8));
            }


            @Override
            public String getWorkerIdentifier()
            {
                return WORKER_NAME;
            }


            @Override
            public int getWorkerApiVersion()
            {
                return WORKER_API_VER;
            }
        };
    }


    private class TestWorkerQueueProvider implements WorkerQueueProvider
    {
        private final BlockingQueue<byte[]> results;


        public TestWorkerQueueProvider(final BlockingQueue<byte[]> results)
        {
            this.results = results;
        }


        @Override
        public final TestWorkerQueue getWorkerQueue(final ConfigurationSource configurationSource, final int maxTasks)
        {
            return new TestWorkerQueue(this.results);
        }
    }


    private class TestWorkerQueue implements ManagedWorkerQueue
    {
        private TaskCallback callback;
        private final BlockingQueue<byte[]> results;
        private String lastQueue;



        public TestWorkerQueue(final BlockingQueue<byte[]> results)
        {
            this.results = results;
        }


        @Override
        public void start(final TaskCallback callback)
            throws QueueException
        {
            this.callback = Objects.requireNonNull(callback);
        }


        @Override
        public void publish(String acknowledgeId, byte[] taskMessage, String targetQueue, Map<String, Object> headers)
            throws QueueException
        {
            this.lastQueue = targetQueue;
            results.offer(taskMessage);
        }


        @Override
        public void rejectTask(final String taskId)
        {
        }


        @Override
        public void discardTask(String messageId) {
        }

        @Override
        public void acknowledgeTask(String messageId) {   
        }

        @Override
        public String getInputQueue() {
            return QUEUE_IN;
        }


        @Override
        public void shutdownIncoming()
        {
        }


        @Override
        public void shutdown()
        {
        }


        @Override
        public WorkerQueueMetricsReporter getMetrics()
        {
            return Mockito.mock(WorkerQueueMetricsReporter.class);
        }


        @Override
        public HealthResult healthCheck()
        {
            return HealthResult.RESULT_HEALTHY;
        }


        public String getLastQueue()
        {
            return lastQueue;
        }


        public void triggerAbort()
        {
            callback.abortTasks();
        }


        public void submitTask(final String taskId, final byte[] stuff)
            throws WorkerException
        {
            callback.registerNewTask(taskId, stuff, new HashMap<>());
        }

    }



    private class SlowWorker extends AbstractWorker<TestWorkerTask, TestWorkerResult>
    {
        private final CountDownLatch latch;


        public SlowWorker(final TestWorkerTask task, final String resultQueue, final Codec codec, final CountDownLatch latch)
            throws WorkerException
        {
            super(task, resultQueue, codec);
            this.latch = Objects.requireNonNull(latch);
        }


        @Override
        public WorkerResponse doWork()
            throws InterruptedException
        {
            try {
                System.out.println("Starting test work");
                Thread.sleep(10000);
                TestWorkerResult result = new TestWorkerResult();
                result.setResultString(SUCCESS);
                return createSuccessResult(result);
            } catch (InterruptedException e) {
                System.out.println("Test work interrupted");
                latch.countDown();
                throw e;
            }
        }


        @Override
        public String getWorkerIdentifier()
        {
            return WORKER_NAME;
        }


        @Override
        public int getWorkerApiVersion()
        {
            return WORKER_API_VER;
        }
    }
}
