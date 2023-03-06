package de.tubs.cs.ias.appanalyzer.analysis.actors.consent

import de.tubs.cs.ias.appanalyzer.Config
import de.tubs.cs.ias.appanalyzer.analysis.Analysis
import de.tubs.cs.ias.appanalyzer.analysis.actors.Actor
import de.tubs.cs.ias.appanalyzer.analysis.actors.consent.ConsentDialogActions._
import de.tubs.cs.ias.appanalyzer.analysis.exceptions.{
  AnalysisFatal,
  MissingInterfaceElement
}
import de.tubs.cs.ias.appanalyzer.analysis.interaction.InteractionTypes.PRESS
import de.tubs.cs.ias.appanalyzer.analysis.interaction.{
  Interface,
  InterfaceElementInteraction
}
import de.tubs.cs.ias.appanalyzer.platform.appium.Appium
import de.tubs.cs.ias.appanalyzer.platform.exceptions.AppClosedItself
import wvlet.log.LogSupport

import java.time.ZonedDateTime
import scala.util.matching.Regex

class ConsentDialogAcceptRejectActor(reject: Boolean,
                                     accept: Boolean,
                                     collectTraffic: Boolean,
                                     conf: Config)
    extends Actor
    with LogSupport {

  info(
    s"preparing consent dialog actor with accept:$accept reject:$reject collect:$collectTraffic")

  private val keywords: Keywords =
    Keywords.read(conf.consentDialogAnalysis.indicatorsFile)

  private var uniqueAcceptButton: Option[String] = None
  private var uniqueRejectButton: Option[String] = None
  private var suspectedConsentDialog: Boolean = false

  private var actions: List[ConsentDialogActions.ConsentDialogAction] = List()

  actions = List(INITIALIZE)
  if (collectTraffic) {
    actions = actions ++ List(ConsentDialogActions.COLLECT_TRAFFIC)
  }

  override def getDescription: String = actions.head match {
    case INITIALIZE      => "[CDA] initial dialog analysis"
    case COLLECT_TRAFFIC => "[CDA] initial traffic collection"
    case REJECT_CONSENT  => "[CDA] rejecting consent"
    case ACCEPT_CONSENT  => "[CDA] accepting consent"
    case BRANCH_DONE =>
      throw new RuntimeException(
        "there shouldn't be a description call when the state is BRANCH_DONE")
  }

  private def initialize(interface: Interface): Unit = {
    val textElements =
      interface.getElements.keySet.map(_.getText.toLowerCase.trim).toList
    suspectedConsentDialog = ConsentDialogAcceptRejectActor
      .determineConsentDialog(textElements,
                              keywords,
                              conf.consentDialogAnalysis.keywordThreshold)
    uniqueAcceptButton = ConsentDialogAcceptRejectActor
      .determineUniqueAcceptButton(textElements,
                                   keywords,
                                   conf.consentDialogAnalysis.maxSizeFactor)
    uniqueRejectButton = ConsentDialogAcceptRejectActor
      .determineUniqueRejectButton(textElements,
                                   keywords,
                                   conf.consentDialogAnalysis.maxSizeFactor)
    if (suspectedConsentDialog) {
      info("we suspect that we have a consent dialog")
      if (uniqueRejectButton.nonEmpty && reject) {
        info(s"and we have a unique reject button: ${uniqueRejectButton.get}")
        actions = actions ++ List(REJECT_CONSENT, BRANCH_DONE)
      }
      if (uniqueAcceptButton.nonEmpty && accept) {
        info(s"and we have a unique accept button: ${uniqueAcceptButton.get}")
        actions = actions ++ List(ACCEPT_CONSENT, BRANCH_DONE)
      }
    } else {
      info("there does not seem to be a consent dialog")
    }
  }

  private def interact(indicator: String, interface: Interface): Int = {
    info(s"performing dialog interaction with '$indicator'")
    interface.getElements.find(_._1.getText.toLowerCase.trim == indicator) match {
      case Some(element) =>
        element._1.click()
        element._2
      case None =>
        throw MissingInterfaceElement(
          indicator,
          interface.getElements.map(_._1.getText.trim).mkString(","))
    }
  }

  private def rejectConsent(interface: Interface): Int = {
    info("refusing consent")
    uniqueRejectButton match {
      case Some(text) =>
        interact(text, interface)
      case None =>
        throw new RuntimeException(
          "reject consent called but we did not detect a reject button")
    }
  }

  private def giveConsent(interface: Interface): Int = {
    info("giving consent")
    uniqueAcceptButton match {
      case Some(text) =>
        interact(text, interface)
      case None =>
        throw new RuntimeException(
          "give consent called but we did not detect an accept button")
    }
  }

  private def interactWithDialog(interface: Interface,
                                 comment: String,
                                 interaction: Interface => Int)(
      implicit context: Analysis,
      appium: Appium): Option[InterfaceElementInteraction] = {
    try {
      info(s"starting dialog interaction: $comment")
      if (collectTraffic) {
        context
          .stopTrafficCollection() // as we have already started a dumb collection on startup
        context.startTrafficCollection(Some(interface), comment)
      }
      val elementId
        : Int = interaction(interface) // here we press the presumed reject/accept button
      try {
        Thread.sleep(5000) // we now wait for 5 seconds to check if the app died
        context.collectCurrentAppPreferences(comment, interface.getOptionalId)
        context.checkIfAppIsStillRunning(true)
        if (collectTraffic) {
          val remainingWaitTime = getRemainingWaitTime(
            context.getTrafficCollectionStart,
            conf.initialCollectionTimeMilli)
          info(s"we have ${remainingWaitTime}ms remaining wait time")
          Thread.sleep(remainingWaitTime)
          context.stopTrafficCollection()
        }
        context.checkIfAppIsStillRunning(true)
        info("extracting newly discovered interface")
        Some(
          new InterfaceElementInteraction(
            PRESS,
            interface,
            elementId,
            // given that we do not want to continue working on this interface we can keep it flat
            Some(Interface(context, appium, flat = true, comment))
          ))
      } catch {
        case _: AppClosedItself =>
          info("app closed itself after interaction")
          assert(actions.head == BRANCH_DONE)
          actions = actions.tail //removing the next action as it would be BRANCH_DONE
          Some(
            new InterfaceElementInteraction(
              PRESS,
              interface,
              elementId,
              None
            ))
      } finally {
        info("dialog interaction done")
        if (collectTraffic) // should be covered by previous stop but ensures that even if there is an error we stop
          context.stopTrafficCollection()
      }
    } catch {
      case x: MissingInterfaceElement =>
        error(x.getMessage)
        context.addEncounteredError(x, None)
        throw AnalysisFatal(
          "expected interface element missing, abort analysis of app")
    }
  }

  private def getRemainingWaitTime(collectionStart: ZonedDateTime,
                                   overallWaitMs: Long): Long = {
    val timeSinceCollectionStart: Long = ZonedDateTime
      .now()
      .toEpochSecond - collectionStart.toEpochSecond
    List(overallWaitMs - (timeSinceCollectionStart * 1000), 0L).max
  }

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
      appium: Appium): Option[InterfaceElementInteraction] =
    try {
      actions match {
        case ::(head, next) =>
          actions = next
          head match {
            case BRANCH_DONE =>
              info("end of dialog interaction path...")
              None
            case INITIALIZE =>
              info("initializing actor based on initial interface")
              context.collectCurrentAppPreferences("pre interaction",
                                                   interface.getOptionalId)
              initialize(interface)
              None
            case COLLECT_TRAFFIC =>
              info("performing initial traffic collection")
              // traffic collection has started already
              val remainingWaitTime = getRemainingWaitTime(
                context.getTrafficCollectionStart,
                conf.initialCollectionTimeMilli)
              info(s"we have ${remainingWaitTime}ms remaining wait time")
              Thread.sleep(remainingWaitTime)
              context.checkIfAppIsStillRunning(true)
              None
            case REJECT_CONSENT =>
              info("conduction reject consent analysis")
              // we now reject the consent
              interactWithDialog(interface, "reject consent", rejectConsent)
            case ACCEPT_CONSENT =>
              info("conduction give consent analysis")
              // we now give the consent
              interactWithDialog(interface, "give consent", giveConsent)
          }
        case Nil => None
      }
    } finally {
      // we always want to do either dumb or non dumb traffic collection (i.e., having an active proxy)
      context.stopTrafficCollection()
    }

  /** check if actor wants to run again on the same app
    *
    * the first element indicates if the actor wants to run on the same app again
    * the second element indicates if the app should be reset before restarting
    *
    * @return
    */
  override def restartApp: (Boolean, Boolean) = {
    // if we still have actions remaining we want to restart the app fully
    (actions.nonEmpty, true)
  }

  /** actions the actor can perform just before the app is being started
    *
    * @param context the analysis in which context the app is started
    */
  override def onAppStartup(implicit context: Analysis): Unit = {
    actions.head match {
      case COLLECT_TRAFFIC =>
        context.startTrafficCollection(None, "[CDA] initial traffic collection")
      case _ =>
        context.startDummyTrafficCollection()
    }
  }

}

object ConsentDialogAcceptRejectActor {

  protected def filterElements(elements: List[String],
                               regex: List[Regex],
                               lengthFactor: Option[Double],
                               blockedRegexp: List[Regex]): List[String] = {
    // remove elements that are blocked
    elements
      .filterNot(element =>
        blockedRegexp.exists(_.findFirstIn(element).nonEmpty))
      .flatMap { element =>
        regex
          .filter { regex =>
            // the matched text must not be longer than <lengthFactor> times the regexp (i.e., no infinitely large buttons)
            (if (lengthFactor.nonEmpty)
               regex.regex.length * lengthFactor.get > element.length
             else true) &&
            // must match the regexp
            regex.findFirstIn(element).nonEmpty
          }
          .map(regex => (regex, element))
      }
      .map(_._2)
  }

  def determineConsentDialog(elements: List[String],
                             keywords: Keywords,
                             threshold: Int): Boolean = {
    // if we have a clear dialog or link
    ConsentDialogAcceptRejectActor
      .filterElements(
        elements,
        keywords.getDialogKeywords ++ keywords.getLinkKeywords,
        None,
        Nil
      )
      .nonEmpty ||
    // or if we have sufficiently many keywords
    (ConsentDialogAcceptRejectActor
      .filterElements(
        elements,
        keywords.getRegularKeywords,
        None,
        Nil
      )
      .length + ConsentDialogAcceptRejectActor
      .filterElements(
        elements,
        keywords.getHalfKeywords,
        None,
        Nil
      )
      .length * 0.5 >= threshold)
  }

  def determineUniqueAcceptButton(elements: List[String],
                                  keywords: Keywords,
                                  threshold: Double): Option[String] = {
    val candidates = filterElements(elements,
                                    keywords.getButtonClearAffirmativeKeywords,
                                    Some(threshold),
                                    keywords.getNegatorKeywords)
    if (candidates.length == 1) {
      Some(candidates.head)
    } else {
      None
    }
  }

  def determineUniqueRejectButton(elements: List[String],
                                  keywords: Keywords,
                                  threshold: Double): Option[String] = {
    val candidates = filterElements(elements,
                                    keywords.getButtonClearNegativeKeywords,
                                    Some(threshold),
                                    Nil)
    if (candidates.length == 1) {
      Some(candidates.head)
    } else {
      None
    }
  }

}
