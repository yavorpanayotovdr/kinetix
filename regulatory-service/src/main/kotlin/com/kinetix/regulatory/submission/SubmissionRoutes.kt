package com.kinetix.regulatory.submission

import com.kinetix.regulatory.submission.dto.ApproveSubmissionRequest
import com.kinetix.regulatory.submission.dto.CreateSubmissionRequest
import com.kinetix.regulatory.submission.dto.SubmissionResponse
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.patch
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant

fun Route.submissionRoutes(service: SubmissionService) {
    route("/api/v1/submissions") {
        post({
            summary = "Create a new regulatory submission"
            tags = listOf("Submissions")
        }) {
            val request = call.receive<CreateSubmissionRequest>()
            val submission = service.create(
                reportType = request.reportType,
                preparerId = request.preparerId,
                deadline = Instant.parse(request.deadline),
            )
            call.respond(HttpStatusCode.Created, submission.toResponse())
        }

        get({
            summary = "List all submissions"
            tags = listOf("Submissions")
        }) {
            val submissions = service.listAll()
            call.respond(submissions.map { it.toResponse() })
        }

        patch("/{id}/review", {
            summary = "Submit for review"
            tags = listOf("Submissions")
            request {
                pathParameter<String>("id") { description = "Submission identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val updated = service.submitForReview(id)
            call.respond(updated.toResponse())
        }

        patch("/{id}/approve", {
            summary = "Approve a submission (four-eyes enforced)"
            tags = listOf("Submissions")
            request {
                pathParameter<String>("id") { description = "Submission identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val request = call.receive<ApproveSubmissionRequest>()
            val updated = service.approve(id, request.approverId)
            call.respond(updated.toResponse())
        }

        patch("/{id}/submit", {
            summary = "Final submit"
            tags = listOf("Submissions")
            request {
                pathParameter<String>("id") { description = "Submission identifier" }
            }
        }) {
            val id = call.parameters["id"]
                ?: throw IllegalArgumentException("Missing required path parameter: id")
            val updated = service.submit(id)
            call.respond(updated.toResponse())
        }
    }
}

private fun RegulatorySubmission.toResponse() = SubmissionResponse(
    id = id,
    reportType = reportType,
    status = status.name,
    preparerId = preparerId,
    approverId = approverId,
    deadline = deadline.toString(),
    submittedAt = submittedAt?.toString(),
    acknowledgedAt = acknowledgedAt?.toString(),
    createdAt = createdAt.toString(),
)
