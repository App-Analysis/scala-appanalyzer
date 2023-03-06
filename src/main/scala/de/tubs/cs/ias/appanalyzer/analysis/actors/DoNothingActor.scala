package de.tubs.cs.ias.appanalyzer.analysis.actors
import de.tubs.cs.ias.appanalyzer.analysis.Analysis
import de.tubs.cs.ias.appanalyzer.analysis.interaction.{
  Interface,
  InterfaceElementInteraction
}
import de.tubs.cs.ias.appanalyzer.platform.appium.Appium

import scala.io.StdIn.readLine

class DoNothingActor extends Actor {

  override def getDescription: String = "I ain't doing anything"

  /** given an interface it performs an action on said interface
    *
    * if none is returned it means the actor is done
    * if Some is returned but the interface is is None it means the app closed after action
    *
    * @param interface the interface on which to perform the action
    * @return the performed action and resulting interface combination
    */
  override def action(interface: Interface)(
      implicit context: Analysis,
      appium: Appium): Option[InterfaceElementInteraction] = {
    try {
      readLine("Press enter to stop.")
      None
    } finally {
      context.stopTrafficCollection()
    }
  }

  /** check if actor wants to run again on the same app
    *
    * the first element indicates if the actor wants to run on the same app again
    * the second element indicates if the app should be reset before restarting
    *
    * @return
    */
  override def restartApp: (Boolean, Boolean) = (false, false)

  /** actions the actor can perform just before the app is being started
    *
    * @param context the analysis in which context the app is started
    */
  override def onAppStartup(implicit context: Analysis): Unit =
    context.startDummyTrafficCollection()
}
