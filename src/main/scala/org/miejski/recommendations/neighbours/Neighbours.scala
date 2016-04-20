package org.miejski.recommendations.neighbours

import org.miejski.recommendations.correlation.{NeighboursDetails, PearsonCorrelation}
import org.miejski.recommendations.evaluation.model.User

case class Neighbours(topNeighbours: Map[String, Seq[NeighbourInfo]]) extends Serializable

object Neighbours {

  def findFor(neighbours: Neighbours, userId: String, top: Int = 5): List[NeighbourInfo] = {
    implicit val ord: Ordering[NeighbourInfo] = new Ordering[NeighbourInfo] {
      override def compare(x: NeighbourInfo, y: NeighbourInfo): Int = y.similarity.compare(x.similarity)
    }
    neighbours.topNeighbours.getOrElse(userId, Seq.empty).sorted.take(top).toList
  }

  def getUserAverageRating(neighbours: Neighbours, user: String) = {
    val topNeighbours = neighbours.topNeighbours

    topNeighbours.filter(s => !s._1.equals(user))
      .map(s => s._2.filter(_.neighbourName.equals(user)).head)
      .map(s => UserAverageRating(s.neighbourName, s.neighbourAverageRating))
      .head
  }

  def printNeighbours(neighbours: Neighbours, user: String, top: Int = 5) = {
    println(s"Neighbours for user: $user")
    findFor(neighbours, user).take(top).foreach(println)
  }

  def sameUsers: ((User, User)) => Boolean = { s => !s._1.id.equals(s._2.id) }

  def fromUsers(userRatings: Seq[(User, User)],
                weighting: (Double, Int, Int) => Double = PearsonCorrelation.significanceWeighting): Neighbours = {
    val userPairNeighbourDetails = userRatings.map(x => ((x._1.id, x._2.id), PearsonCorrelation.compute(x._1.ratings, x._2.ratings, weighting)))
    val topNeighboursMap = bidirectionalNeighboursMapping(userPairNeighbourDetails)
    new Neighbours(topNeighboursMap)
  }

  def bidirectionalNeighboursMapping(uniqueUsersCorrelations: Seq[((String, String), NeighboursDetails)]): Map[String, Seq[NeighbourInfo]] = {
    val map = uniqueUsersCorrelations.filter(c => !c._1._1.equals(c._1._2))
      .flatMap(corr => Seq(
        (corr._1._1, NeighbourInfo(corr._1._2, corr._2.similarity, corr._2.user2SharedAverageRating, corr._2.user2AverageRating)),
        (corr._1._2, NeighbourInfo(corr._1._1, corr._2.similarity, corr._2.user1SharedAverageRating, corr._2.user1AverageRating))))

    val r = map.groupBy(_._1).mapValues(s => s.map(x => x._2).sortBy(singleCorr => singleCorr.similarity).reverse)
    r
  }
}

case class NeighbourInfo(neighbourName: String,
                         similarity: Double,
                         neighbourSharedAverageRating: Double,
                         neighbourAverageRating: Double) extends Serializable

case class UserAverageRating(user: String, averageRating: Double)