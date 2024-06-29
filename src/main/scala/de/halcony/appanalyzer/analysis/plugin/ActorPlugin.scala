package de.halcony.appanalyzer.analysis.plugin

import de.halcony.appanalyzer.analysis.Analysis
import de.halcony.appanalyzer.analysis.interaction.{
  Interface,
  InterfaceElementInteraction
}
import de.halcony.appanalyzer.platform.appium.Appium

trait ActorPlugin {

  /** provide the plugin with the command line parameters
    *
    * @param parameter
    *   a map of parameter to set/interpret by the plugin
    * @return
    *   the configured actor plugin
    */
  def setParameter(parameter: Map[String, String]): ActorPlugin

  def getDescription: String

  /** given an interface it performs an action on said interface
    *
    * if none is returned it means the actor is done if Some is returned but the
    * interface is is None it means the app closed after action
    *
    * @param interface
    *   the interface on which to perform the action
    * @return
    *   the performed action and resulting interface combination
    */
  def action(interface: Interface)(implicit
      context: Analysis,
      appium: Appium
  ): Option[InterfaceElementInteraction]

  /** check if actor wants to run again on the same app
    *
    * the first element indicates if the actor wants to run on the same app
    * again the second element indicates if the app should be reset before
    * restarting
    *
    * @return
    */
  def restartApp: (Boolean, Boolean)

  /** actions the actor can perform just before the app is being started
    *
    * @param context
    *   the analysis in which context the app is started
    */
  def onAppStartup(implicit context: Analysis): Unit

}
