package com.kinetix.risk.model.report

class ReportTemplateNotFoundException(templateId: String) :
    Exception("Report template not found: $templateId")

class ReportRowLimitExceededException(actual: Int, limit: Int) :
    Exception("Report row count $actual exceeds limit of $limit")
