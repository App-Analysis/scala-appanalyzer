package de.tubs.cs.ias.appanalyzer.analysis.actors
import de.tubs.cs.ias.appanalyzer.analysis.Analysis
import de.tubs.cs.ias.appanalyzer.analysis.interaction.{Interface, InterfaceElementInteraction}
import de.tubs.cs.ias.appanalyzer.platform.appium.Appium
import wvlet.log.LogSupport

import scala.io.StdIn.readLine

class SimpleTrafficCollection(timems: Long) extends Actor with LogSupport {

  override def getDescription: String = "Simple Traffic Collection"

  /** given an interface it performs an action on said interface
    *
    * if none is returned it means the actor is done
    * if Some is returned but the interface is is None it means the app closed after action
    *
    * @param interface the interface on which to perform the action
    * @return the performed action and resulting interface combinationx
    */
  override def action(interface: Interface)(
      implicit context: Analysis,
      appium: Appium): Option[InterfaceElementInteraction] = {
    context.checkIfAppIsStillRunning(true)
    if(timems == -1) {
      info("starting infinity monitoring")
      readLine("press enter to stop.")
    } else {
      info(s"waiting for $timems ms")
      Thread.sleep(timems)
    }
    // wait for the specified time
    context.checkIfAppIsStillRunning(true)
    None // tell the analysis that you are done
  }

  /** check if actor wants to run again on the same app
    *
    * the first element indicates if the actor wants to run on the same app again
    * the second element indicates if the app should be reset before restarting
    *
    * @return
    */
  override def restartApp: (Boolean, Boolean) = (false, false)

  override def onAppStartup(implicit context: Analysis): Unit = {
    // we want to start collecting traffic as soon as the app starts
    context.startTrafficCollection(None, "Simple Traffic Collection")
  }
}
