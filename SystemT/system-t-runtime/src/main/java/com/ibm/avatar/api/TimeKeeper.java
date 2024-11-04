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
package com.ibm.avatar.api;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The <code>TimeKeeper</code> thread is used to enforce the check that the System T annotation
 * engine only gets 2 seconds of wall clock time to extract as much as possible from a given
 * document.
 * 
 */
public class TimeKeeper implements Runnable {
  private static long TIME_BUDGET = 2000;
  private Thread execThread;
  private Logger logger;

  public TimeKeeper(Logger logger) {
    this.logger = logger;
    reset(null);
  }

  public void reset(Thread execThread) {
    this.execThread = execThread;
  }

  @Override
  public void run() {
    try {
      logger.log(Level.INFO, "Going to sleep at " + System.currentTimeMillis());
      Thread.sleep(TIME_BUDGET);
      logger.log(Level.INFO, "Waking up at " + System.currentTimeMillis());
    } catch (InterruptedException e) {
      logger.log(Level.INFO, "Interrupted by main thread -- doc was annotated within time budget");
      return;
    }
    logger.log(Level.INFO, "Budgeted time has expired -- interrupting main thread");
    if (execThread != null) {
      execThread.interrupt();
    }
  }
}
