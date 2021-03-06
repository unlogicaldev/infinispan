/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.distexec;

import org.infinispan.Cache;
import org.infinispan.config.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.test.AbstractCacheTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.infinispan.util.concurrent.WithinThreadExecutor;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Tests basic org.infinispan.distexec.DistributedExecutorService functionality
 * 
 * @author Vladimir Blagojevic
 * @author Anna Manukyan
 */
@Test(groups = "functional", testName = "distexec.BasicDistributedExecutorTest")
public class BasicDistributedExecutorTest extends AbstractCacheTest {

   public BasicDistributedExecutorTest() {
   }
   
   @Test(expectedExceptions = { IllegalStateException.class })
   public void testImproperCacheStateForDistribtuedExecutor() {
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createLocalCacheManager(false);
      try {
         Cache<Object, Object> cache = cacheManager.getCache();
         DistributedExecutorService des = new DefaultExecutorService(cache);
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }
   
   @Test(expectedExceptions = { IllegalArgumentException.class })
   public void testImproperMasterCacheForDistributedExecutor() {
      DistributedExecutorService des = new DefaultExecutorService(null);
      
   }

   @Test(expectedExceptions = { IllegalArgumentException.class })
   public void testImproperLocalExecutorServiceForDistributedExecutor() {
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createLocalCacheManager(false);
      try {
         Cache<Object, Object> cache = cacheManager.getCache();
         DistributedExecutorService des = new DefaultExecutorService(cache, null);
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   @Test(expectedExceptions = { IllegalArgumentException.class })
   public void testStoppedLocalExecutorServiceForDistributedExecutor() {
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createLocalCacheManager(false);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> cache = cacheManager.getCache();

         ExecutorService service = new WithinThreadExecutor();
         service.shutdown();

         des = new DefaultExecutorService(cache, service);
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   @Test(expectedExceptions = { IllegalStateException.class })
   public void testStoppedCacheForDistributedExecutor() {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      try {
         Cache<Object, Object> cache = cacheManager.getCache();
         cache.stop();
         DistributedExecutorService des = new DefaultExecutorService(cache);
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   public void testDistributedExecutorShutDown() {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> cache = cacheManager.getCache();
         des = new DefaultExecutorService(cache);
         des.shutdown();
         assert des.isShutdown();
         assert des.isTerminated();
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   public void testDistributedExecutorShutDownNow() {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      try {
         Cache<Object, Object> cache = cacheManager.getCache();
         DistributedExecutorService des = new DefaultExecutorService(cache);

         assert !des.isShutdown();
         assert !des.isTerminated();

         des.shutdownNow();

         assert des.isShutdown();
         assert des.isTerminated();
      } finally {
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   /**
    * Tests that we can invoke DistributedExecutorService on an Infinispan cluster having a single node
    * 
    * @throws Exception
    */
   public void testSingleCacheExecution() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> cache = cacheManager.getCache();
         des = new DefaultExecutorService(cache);
         Future<Integer> future = des.submit(new SimpleCallable());
         Integer r = future.get();
         assert r == 1;

         List<Future<Integer>> list = des.submitEverywhere(new SimpleCallable());
         AssertJUnit.assertEquals(1, list.size());
         for (Future<Integer> f : list) {
            AssertJUnit.assertEquals(new Integer(1), f.get());
         }
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager);
      }
   }
   
   /**
    * Tests that we can invoke DistributedExecutorService task with keys
    * https://issues.jboss.org/browse/ISPN-1886
    * 
    * @throws Exception
    */
   public void testSingleCacheWithKeysExecution() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> c1 = cacheManager.getCache();
         c1.put("key1", "Manik");
         c1.put("key2", "Mircea");
         c1.put("key3", "Galder");
         c1.put("key4", "Sanne");

         des = new DefaultExecutorService(c1);

         Future<Boolean> future = des.submit(new SimpleDistributedCallable(true), new String[] {
                  "key1", "key2" });
         Boolean r = future.get();
         assert r;
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager);
      }
   }
   
   public void testDistributedCallableCustomFailoverPolicySuccessfullRetry() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> cache1 = cacheManager.getCache();
         cache1.put("key1", "value1");
         cache1.put("key2", "value2");

         //initiate task from cache1 and select cache1 as target
         des = new DefaultExecutorService(cache1);

         //the same using DistributedTask API
         DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(new FailOnlyOnceCallable());
         taskBuilder.failoverPolicy(new DistributedTaskFailoverPolicy() {

            @Override
            public Address failover(FailoverContext context) {
               return context.executionFailureLocation();
            }

            @Override
            public int maxFailoverAttempts() {
               return 1;
            }
         });

         DistributedTask<Integer> task = taskBuilder.build();
         AssertJUnit.assertEquals(1, task.getTaskFailoverPolicy().maxFailoverAttempts());
         Future<Integer> val = des.submit(task, new String[] { "key1" });
         AssertJUnit.assertEquals(new Integer(1), val.get());
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   @Test(expectedExceptions = ExecutionException.class)
   public void testDistributedCallableEmptyFailoverPolicy() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> cache1 = cacheManager.getCache();

         //initiate task from cache1 and select cache1 as target
         des = new DefaultExecutorService(cache1);

         //the same using DistributedTask API
         DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(new ExceptionThrowingCallable());
         taskBuilder.failoverPolicy(null);
         DistributedTask<Integer> task = taskBuilder.build();

         assert task.getTaskFailoverPolicy().equals(DefaultExecutorService.NO_FAILOVER);

         Future<Integer> f = des.submit(task);

         f.get();
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   @Test(expectedExceptions = ExecutionException.class)
   public void testDistributedCallableRandomFailoverPolicy() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;

      try {
         Cache<Object, Object> cache1 = cacheManager.getCache();
         cache1.put("key1", "value1");
         cache1.put("key2", "value2");

         //initiate task from cache1 and select cache1 as target
         des = new DefaultExecutorService(cache1);

         //the same using DistributedTask API
         DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(new FailOnlyOnceCallable());
         taskBuilder.failoverPolicy(DefaultExecutorService.RANDOM_NODE_FAILOVER);

         DistributedTask<Integer> task = taskBuilder.build();

         assert task.getTaskFailoverPolicy().equals(DefaultExecutorService.RANDOM_NODE_FAILOVER);

         Future<Integer> val = des.submit(task, new String[] {"key1"});

         val.get();
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager);
      }
   }

   @Test(expectedExceptions = ExecutionException.class)
   public void testDistributedCallableRandomFailoverPolicyWith2Nodes() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      EmbeddedCacheManager cacheManager1 = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;
      try {
         Cache<Object, Object> cache1 = cacheManager.getCache();
         cache1.put("key1", "value1");
         cache1.put("key2", "value2");

         Cache<Object, Object> cache2 = cacheManager1.getCache();
         cache2.put("key3", "value3");

         //initiate task from cache1 and select cache1 as target
         des = new DefaultExecutorService(cache1);

         //the same using DistributedTask API
         DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(new ExceptionThrowingCallable());
         taskBuilder.failoverPolicy(DefaultExecutorService.RANDOM_NODE_FAILOVER);

         DistributedTask<Integer> task = taskBuilder.build();

         assert task.getTaskFailoverPolicy().equals(DefaultExecutorService.RANDOM_NODE_FAILOVER);

         Future<Integer> val = des.submit(task, new String[] {"key1"});
         val.get();
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager, cacheManager1);
      }
   }

   @Test(expectedExceptions = ExecutionException.class)
   public void testDistributedCallableCustomFailoverPolicy() throws Exception {
      Configuration config = TestCacheManagerFactory.getDefaultConfiguration(true, Configuration.CacheMode.REPL_SYNC);
      EmbeddedCacheManager cacheManager = TestCacheManagerFactory.createClusteredCacheManager(config);
      DistributedExecutorService des = null;

      try {
         Cache<Object, Object> cache1 = cacheManager.getCache();
         cache1.put("key1", "value1");
         cache1.put("key2", "value2");

         //initiate task from cache1 and select cache1 as target
         des = new DefaultExecutorService(cache1);

         //the same using DistributedTask API
         DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(new FailOnlyOnceCallable());

         taskBuilder.failoverPolicy(new DistributedTaskFailoverPolicy() {

            @Override
            public Address failover(FailoverContext context) {
               return context.executionFailureLocation();
            }

            @Override
            public int maxFailoverAttempts() {
               return 0;
            }
         });

         DistributedTask<Integer> task = taskBuilder.build();
         assert task.getTaskFailoverPolicy().maxFailoverAttempts() == 0;

         Future<Integer> val = des.submit(task, new String[] {"key1"});
         val.get();
      } finally {
         des.shutdownNow();
         TestingUtil.killCacheManagers(cacheManager);
      }
   }


   static class SimpleDistributedCallable implements DistributedCallable<String, String, Boolean>,
            Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = 623845442163221832L;
      private boolean invokedProperly = false;
      private final boolean hasKeys;

      public SimpleDistributedCallable(boolean hasKeys) {
         this.hasKeys = hasKeys;
      }

      @Override
      public Boolean call() throws Exception {
         return invokedProperly;
      }

      @Override
      public void setEnvironment(Cache<String, String> cache, Set<String> inputKeys) {
         boolean keysProperlySet = hasKeys ? inputKeys != null && !inputKeys.isEmpty()
                  : inputKeys != null && inputKeys.isEmpty();
         invokedProperly = cache != null && keysProperlySet;
      }

      public boolean validlyInvoked() {
         return invokedProperly;
      }
   }

   static class SimpleCallable implements Callable<Integer>, Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = -8589149500259272402L;

      public SimpleCallable() {
      }

      @Override
      public Integer call() throws Exception {
         return 1;
      }
   }
   
   static class FailOnlyOnceCallable implements Callable<Integer>, Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = 3961940091247573385L;
      boolean throwException = true;

      public FailOnlyOnceCallable() {
         super();         
      }

      @Override
      public Integer call() throws Exception {
         if (throwException) {
            // do to not throw the exception 2nd time during retry.
            throwException = false;
            // now throw exception for the first run 
            int a = 5 / 0;
         }
         return 1;
      }
   }

   static class SimpleSleepingDistributedCallable implements DistributedCallable<String, String, Boolean>, Serializable{

      @Override
      public void setEnvironment(Cache<String, String> cache, Set<String> inputKeys) {
         //do nothing
      }

      @Override
      public Boolean call() throws Exception {
         Thread.sleep(1000);

         return true;
      }
   }

   static class ExceptionThrowingCallable implements Callable<Integer>, Serializable {

      /** The serialVersionUID */
      private static final long serialVersionUID = -8589149500259272402L;

      public ExceptionThrowingCallable() {
      }

      @Override
      public Integer call() throws Exception {
         //simulating ArithmeticException
         int a = 5 / 0;

         return 1;
      }
   }
}
