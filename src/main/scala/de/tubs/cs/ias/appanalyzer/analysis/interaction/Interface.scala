package de.tubs.cs.ias.appanalyzer.analysis.interaction

import de.tubs.cs.ias.appanalyzer.analysis.Analysis
import de.tubs.cs.ias.appanalyzer.database.Postgres
import de.tubs.cs.ias.appanalyzer.platform.appium.Appium
import org.apache.commons.io.output.ByteArrayOutputStream
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef
import wvlet.log.LogSupport

import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO

class Interface(elements: List[InterfaceElement],
                belongsTo: Analysis,
                appium: Appium,
                comment: String = "")
    extends LogSupport {

  def getComment: String = comment

  private var id: Option[Int] = None

  def getId: Int = id.get

  def getOptionalId: Option[Int] = id

  private var insertedElements: Option[Map[InterfaceElement, Int]] = None

  def getElements: Map[InterfaceElement, Int] = insertedElements.get

  def getUnmappedElements: List[InterfaceElement] = elements

  /** inserting the given interface into the database
    *
    * requires the context analysis to be already inserted
    *
    */
  def insert(): Unit = {
    info(s"inserting interface $comment")
    Postgres.withDatabaseSession { implicit session =>
      val screenBytes = appium.takeScreenshot match {
        case Some(value) =>
          val baos: ByteArrayOutputStream = new ByteArrayOutputStream()
          ImageIO.write(value, "png", baos)
          Some(baos.toByteArray)
        case None => null
      }
      id = sql"""INSERT INTO interface (
                       analysis,
                       comment,
                       screenshot
                       )
              VALUES (
                      ${belongsTo.getId},
                      $comment,
                      $screenBytes
                      )
              RETURNING id
           """
        .map { entity =>
          entity.int("id")
        }
        .first
        .apply()
      val map = elements.map { element =>
        belongsTo
          .checkStop() //as interacting with the elements of an app can take quite a time we need to check for timeouts
        val text = {
          val buff = element.getText
          try {
            buff.getBytes(StandardCharsets.UTF_8) // check if we have valid UTF-8 text
            buff
          } catch {
            case _: Throwable =>
              warn(s"$buff contains non UTF-8 characters filtering down")
              val rem = buff.sliding(1, 1).filter {
                character =>
                  try {
                    character.getBytes(StandardCharsets.UTF_8)
                    true
                  } catch {
                    case _: Throwable => false
                  }
              }
              warn(s"remaining text is $rem")
              rem
          }
        }
        val clickable = element.isEnabled && element.isDisplayed
        val elementBytes =
          appium.takeElementScreenshot(element.getUnderlyingElement) match {
            case Some(value) =>
              val baos: ByteArrayOutputStream = new ByteArrayOutputStream()
              ImageIO.write(value, "png", baos)
              Some(baos.toByteArray)
            case None => null
          }
        val id = sql"""INSERT INTO InterfaceElement (
                             belongs_to,
                             text,
                             clickable,
                             screenshot
                             )
                  VALUES(
                         ${this.getId},
                         $text,
                         $clickable,
                         $elementBytes
                         )
                  RETURNING Id
               """
          .map { entity =>
            entity.int("id")
          }
          .first
          .apply()
          .get
        element -> id
      }.toMap
      insertedElements = Some(map)
    }
  }
}

object Interface extends LogSupport {

  /** creates a new interface based on the currently displayed one on the device
    *
    * @param analysis the analysis context of the interface
    * @param appium the running appium instance
    * @param flat if set no elements will be extracted (thus only the whole screenshot is stored)
    * @param comment a comment to store alongside the interface in the database
    * @return
    */
  def apply(analysis: Analysis,
            appium: Appium,
            flat: Boolean = false,
            comment: String = ""): Interface = {
    val elements = if (!flat) {
      appium.getAllElements
        .map { elem =>
          analysis.checkStop() // this ensures that even really slow apps will stop at some point
          new InterfaceElement(elem)
        }
        .filter(_.getText.trim != "")
    } else {
      warn("we extract a flat interface only")
      List()
    }
    new Interface(
      elements,
      analysis,
      appium,
      comment
    )
  }

}
