package treeinterpreter

import treeinterpreter.TreeInterpreter._
import DressedTree._
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.tree.model.{RandomForestModel, DecisionTreeModel, Node}
import org.apache.spark.mllib.tree.configuration.Algo._

case class DressedTree(model: DecisionTreeModel, bias: Double, contributionMap: NodeContributions) {

  implicit def nodeType(node: Node) = model.algo match {
    case Classification => ClassificationNode(node)
    case Regression => RegressionNode(node)
  }

  private def predictLeafID(features: Vector): NodeID = model.topNode.predictLeaf(features)

  def predictLeaf(point: Vector) = {
    val leaf = predictLeafID(point)
    val prediction = model.predict(point)
    val contribution = contributionMap(leaf)
    Interp(bias, prediction, contribution)
  }

  def interpret(point: Vector) = predictLeaf(point)
}

object DressedTree {

  type Feature = Option[NodeID]

  type NodeContributions = Map[NodeID, Map[Feature, Double]]

  def arrayprint[A](x: Array[A]) = println(x.deep.mkString("\n"))

  def trainInterpreter(model: DecisionTreeModel) = {
    val topNode = model.topNode

    implicit def nodeType(node: Node) = model.algo match {
      case Classification => ClassificationNode(node)
      case Regression => RegressionNode(node)
    }
    type Path = Array[TreeNode]

    type PathBundle = Array[Path]

    val bias = topNode.value

    var Paths: PathBundle = Array()

    def buildPath(paths: Path, node: Node): PathBundle = {
      val TreeNode = nodeType(node)
      if (node.isLeaf) Array(paths :+ TreeNode)
      else {
        import node._
        val buildRight = buildPath(paths :+ TreeNode, rightNode.get)
        val buildLeft = buildPath(paths :+ TreeNode, leftNode.get)
        buildRight ++ buildLeft
      }
    }
    val paths = buildPath(Array(), topNode)
    val contributions: NodeContributions = paths.map(_.sorted.reverse).flatMap(path => {

      val contribMap = {
        {
          path zip path.tail
        }.flatMap {
          case (currentNode, prevNode) =>
            Map(prevNode.feature -> {
              currentNode.value - prevNode.value
            })
        }
      }.foldLeft(Map[Feature, Double]())(_ + _)
      val leafID = path.head.NodeID
      Map(leafID -> contribMap)
    }).toMap
    DressedTree(model, bias, contributions)
  }
}
