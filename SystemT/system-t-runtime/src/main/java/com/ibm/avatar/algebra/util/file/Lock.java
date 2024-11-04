/*******************************************************************************
 * Copyright IBM
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package com.ibm.avatar.algebra.util.file;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import com.ibm.avatar.api.exceptions.TextAnalyticsException;
import com.ibm.avatar.logging.Log;

/**
 * A thin wrapper over Java NIO's file locking mechanism to acquire a *logical* lock on a shared
 * directory to *indicate* that some process / thread is working on the shared directory and other
 * threads / processes should wait till lock is released.
 * 
 */
public class Lock {
  private static final boolean debug = false;

  /** Name of the lock file */
  public static final String LOCK_FILE = ".lock";

  /** Java NIO's lock to the .lock file */
  private final FileLock lock;

  /** Directory for which a logical lock is acquired */
  private final File dir;

  /**
   * Private constructor to initialize file lock and directory being locked.
   * 
   * @param lock FileLock on .lock file under the specified directory
   * @param dir directory being locked
   */
  private Lock(FileLock lock, File dir) {
    this.lock = lock;
    this.dir = dir;
  }

  /**
   * Acquires a FileLock for a file named ".lock" under the specified directory as an indication
   * that the caller owns exclusive read/write access to the directory till the lock is released.
   * This is a blocking call. Threads or processes calling this method will block until a lock is
   * acquired.
   * 
   * @param dirToLock File descriptor of the directory for which the lock is sought by the caller
   * @return a Lock object representing a logical lock on the specified directory.
   * @throws TextAnalyticsException If there is any problem in acquiring the lock.
   */
  public static synchronized Lock acquire(File dirToLock) throws TextAnalyticsException {
    if (debug) {
      Log.info("Thread [%s] is attempting to acquire lock on directory [%s] ",
          Thread.currentThread().getName(), dirToLock.getAbsolutePath());
    }

    // Fail fast: return quickly if there is no work to do
    if (dirToLock == null || false == dirToLock.exists()) {
      throw new TextAnalyticsException("Attempted to acquire lock on a non-existent directory : %s",
          (dirToLock == null ? null : dirToLock.getAbsolutePath()));
    }

    File lockFile = new File(dirToLock, LOCK_FILE);

    // attempt to create a lock file first.
    // File.createNewFile() is atomic in checking for existence of a file and creating a new file
    try {
      lockFile.createNewFile();
    } catch (IOException cause) {
      throw new TextAnalyticsException(cause, "Unable to acquire lock for directory : %s",
          dirToLock.getAbsolutePath());
    }

    // if control reaches here, it is guaranteed that the .lock file exists

    // acquire a lock
    try {
      @SuppressWarnings("resource")
      RandomAccessFile raf = new RandomAccessFile(lockFile, "rws");
      FileChannel channel = raf.getChannel();

      // blocking call - acquires exclusive access on the file
      FileLock lock = channel.lock();

      if (debug) {
        Log.info("Thread [%s] successfully acquired lock on directory [%s] ",
            Thread.currentThread().getName(), dirToLock.getAbsolutePath());
      }

      return new Lock(lock, dirToLock);
    } catch (Exception cause) {
      throw new TextAnalyticsException(cause, "Unable to acquire lock on directory : %s",
          dirToLock.getAbsolutePath());
    }

  }

  /**
   * Releases the acquired lock.
   * 
   * @throws TextAnalyticsException
   */
  public void release() throws TextAnalyticsException {
    try {
      lock.release();
    } catch (IOException e) {
      throw new TextAnalyticsException(e, "Unable to release lock on directory: %s",
          dir.getAbsolutePath());
    } finally {
      try {
        lock.channel().close();
        if (debug) {
          Log.info("Thread [%s] released lock on directory [%s] ", Thread.currentThread().getName(),
              dir.getAbsolutePath());
        }
      } catch (IOException e) {
        throw new TextAnalyticsException(e, "Unable to release lock in directory : %s",
            dir.getAbsolutePath());
      }
    }
  }

}
