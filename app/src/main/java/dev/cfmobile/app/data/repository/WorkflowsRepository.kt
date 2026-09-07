package dev.cfmobile.app.data.repository

import dev.cfmobile.app.data.remote.ApiResult
import dev.cfmobile.app.data.remote.CloudflareApi
import dev.cfmobile.app.data.remote.dto.WorkflowInstanceStatusWrite
import dev.cfmobile.app.data.remote.dto.CfWorkflow
import dev.cfmobile.app.data.remote.dto.WorkflowInstance
import dev.cfmobile.app.data.remote.safeApiCall

/** Read-only: list deployed Workflows and inspect their recent instances. Workflows are
 *  created by deploying a Worker, and triggering a run is an application concern rather than
 *  an admin one, so neither is offered here. */
class WorkflowsRepository(private val api: CloudflareApi) {

    suspend fun listWorkflows(accountId: String): ApiResult<List<CfWorkflow>> =
        safeApiCall { api.listWorkflows(accountId) }

    suspend fun listInstances(accountId: String, workflowName: String): ApiResult<List<WorkflowInstance>> =
        safeApiCall { api.listWorkflowInstances(accountId, workflowName) }

    /** Starts a new run. Cloudflare generates the instance id. */
    suspend fun triggerInstance(accountId: String, workflowName: String): ApiResult<WorkflowInstance> =
        safeApiCall { api.createWorkflowInstance(accountId, workflowName) }

    /** [status] is one of Cloudflare's verbs: "terminate", "pause", or "resume". */
    suspend fun setInstanceStatus(
        accountId: String,
        workflowName: String,
        instanceId: String,
        status: String
    ): ApiResult<WorkflowInstance> =
        safeApiCall {
            api.updateWorkflowInstanceStatus(accountId, workflowName, instanceId, WorkflowInstanceStatusWrite(status))
        }
}
