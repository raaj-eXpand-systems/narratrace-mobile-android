package io.narratrace.android.core.media

import io.narratrace.android.core.network.*
import org.junit.Assert.*
import org.junit.Test

class ParityFailureTest {
    @Test fun `legacy completed trial conflict requires access without treating role denial as upgrade`() {
        assertTrue(ApiResult.ServerError(ApiErrorCode.FORBIDDEN, "FORBIDDEN", "Your complimentary first story is complete. Choose a plan to create another.", httpStatus = 409, supportReference = "").requiresInterviewAccess())
        assertTrue(ApiResult.Forbidden("Your current plan does not include interviews.", supportReference = "").requiresInterviewAccess())
        assertFalse(ApiResult.Forbidden("Your family role cannot perform this action.", supportReference = "").requiresInterviewAccess())
        assertFalse(ApiResult.Offline().requiresInterviewAccess())
        assertTrue(ApiResult.ServerError(ApiErrorCode.FORBIDDEN, "INTERVIEW_ACCESS_REQUIRED", "Account access changed.", httpStatus = 403, supportReference = "").requiresInterviewAccess())
    }
}
