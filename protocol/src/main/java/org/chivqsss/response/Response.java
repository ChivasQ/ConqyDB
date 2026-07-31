package org.chivqsss.response;

public sealed interface Response permits FoundResponse, NotFoundResponse, OkResponse, ErrorResponse {
}