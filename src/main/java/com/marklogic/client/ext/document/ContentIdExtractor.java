package com.marklogic.client.ext.document;

import java.io.IOException;

import com.marklogic.client.io.marker.AbstractWriteHandle;

public interface ContentIdExtractor {

	String extractId(AbstractWriteHandle content);
	String extractId(AbstractWriteHandle content, String path);
	String extractId(AbstractWriteHandle content, String[] path);
} 