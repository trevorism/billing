package com.trevorism.controller

import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.server.types.files.StreamedFile
import jakarta.inject.Inject

@Controller("/notfound")
class NotFoundRouteController {

    @Inject
    ResourceResolver resourceResolver

    @Error(status = HttpStatus.NOT_FOUND, global = true)
    HttpResponse forward(HttpRequest request) {
        if (request.getHeaders()
                .accept()
                .stream()
                .anyMatch(mediaType -> mediaType.getName().contains(MediaType.TEXT_HTML))) {
            StreamedFile streamedFile = new StreamedFile(resourceResolver.getResource("classpath:public/index.html").get())
            return HttpResponse.ok(streamedFile)
        }
    }
}
