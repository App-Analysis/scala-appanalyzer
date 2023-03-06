package de.tubs.cs.ias.appanalyzer.analysis.interaction

import de.tubs.cs.ias.appanalyzer.analysis.interaction.InteractionTypes._
import de.tubs.cs.ias.appanalyzer.database.Postgres
import scalikejdbc.scalikejdbcSQLInterpolationImplicitDef

class InterfaceElementInteraction(action: InteractionTypes.Interaction,
                                  onInterface: Interface,
                                  onElement: Int,
                                  leadingTo: Option[Interface]) {

  private var id: Option[Int] = None
  def getId: Int = id.get
  def getAction: String = action match {
    case PRESS => "PRESS"
    case SWIPE => "SWIPE"
  }
  def getInterface: Interface = onInterface
  def getElement: InterfaceElement =
    onInterface.getElements.find(_._2 == onElement).get._1
  def getElementId: Int = onElement
  def getLeadingTo: Option[Interface] = leadingTo

  /** insert the interface element interaction
    *
    * todo: this is a somewhat leaky abstraction as both (possible) interfaces have to already been inserted
    *
    */
  def insert(): Unit = {
    Postgres.withDatabaseSession { implicit session =>
      val resultInterface = leadingTo match {
        case Some(value) => value.getId
        case None        => null
      }
      id = sql"""INSERT INTO interfaceelementinteraction (
                                         action,
                                         on_element,
                                         leading_to
                                         )
                VALUES (
                        $getAction,
                        ${this.getElementId},
                        $resultInterface
                        )
                RETURNING id
             """
        .map { entity =>
          entity.int("id")
        }
        .first
        .apply()
    }
  }

}
