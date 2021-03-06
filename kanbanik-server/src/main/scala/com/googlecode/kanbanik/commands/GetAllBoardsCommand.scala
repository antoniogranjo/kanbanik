package com.googlecode.kanbanik.commands

import com.googlecode.kanbanik.builders.{BoardBuilder, ProjectBuilder, TaskBuilder}
import com.googlecode.kanbanik.dtos.{BoardWithProjectsDto, ErrorDto, GetAllBoardsWithProjectsDto, ListDto, _}
import com.googlecode.kanbanik.model.{User, Board, Project, Task}
import org.bson.types.ObjectId

class GetAllBoardsCommand extends Command[GetAllBoardsWithProjectsDto, ListDto[BoardWithProjectsDto]] {

  lazy val boardBuilder = new BoardBuilder()

  lazy val projectBuilder = new ProjectBuilder()

  val taskBuilder = new TaskBuilder

  override def execute(params: GetAllBoardsWithProjectsDto, user: User): Either[ListDto[BoardWithProjectsDto], ErrorDto] = {
    val loadedBoards = Board.all(
      params.includeTasks.getOrElse(false),
      params.includeTaskDescription.getOrElse(false),
      extractFilters(params.filters, x => if (x.bid.isDefined) Some(new ObjectId(x.bid.get)) else None),
      extractFilters(params.filters, _.bname),
      user)

    val pids = extractFilters(params.filters, x => if (x.pid.isDefined) Some(new ObjectId(x.pid.get)) else None)
    val pnames = extractFilters(params.filters, _.pname)

    val loadedProjects = Project.all(
      user,
      pids,
      pnames
    )

    val projectIds = loadedProjects.map(p => p.id.get)
    def taskAllowed(task: Task): Boolean = {
      // since the projectIds contain projects which has already been filtered on DB level
      // this check takes into account also pnames transparently
      return projectIds.contains(task.projectId)
    }

    val res = ListDto(
      loadedBoards.map(
        board => BoardWithProjectsDto(
        boardBuilder.buildDto(board, user).copy(
          tasks = Some(board.tasks.filter(taskAllowed).map(taskBuilder.buildDto))
        ), {
          val projectDtos = loadedProjects.filter(
            project => project.boards.getOrElse(List[Board]()).exists(projectsBoard => projectsBoard.id == board.id)
          ).map(projectOnBoard => projectBuilder.buildDto(projectOnBoard))

          if (projectDtos.size > 0) {
            Some(ProjectsDto(projectDtos))
          } else {
            None
          }
        }
        )
      )
    )

    Left(res)
  }

  def extractFilters[T](filters: Option[List[FilterDto]], f: FilterDto => Option[T]): Option[List[T]] = {
    if (filters.isDefined) {
      val xs = for (x <- filters.get if f(x).isDefined) yield f(x).get
      if (xs.isEmpty) {
        None
      } else {
        Some(xs)
      }
    } else {
      None
    }
  }

}
