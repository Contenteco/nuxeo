package org.nuxeo.ecm.platform.rendition.extension;

import java.util.ArrayList;
import java.util.List;

import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.platform.rendition.RenditionException;
import org.nuxeo.ecm.platform.rendition.service.RenditionDefinition;
import org.nuxeo.runtime.api.Framework;

public class DefaultAutomationRenditionProvider implements RenditionProvider {

    @Override
    public boolean isAvailable(DocumentModel doc, RenditionDefinition def) {
        BlobHolder bh = doc.getAdapter(BlobHolder.class);
        try {
            if (bh == null || bh.getBlob() == null) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public List<Blob> render(DocumentModel doc, RenditionDefinition definition)
            throws RenditionException {

        String chain = definition.getOperationChain();
        if (chain == null) {
            throw new RenditionException("no operation defined");
        }

        AutomationService as = Framework.getLocalService(AutomationService.class);

        OperationContext oc = new OperationContext();

        try {
            oc.push(Constants.O_BLOB,
                    doc.getAdapter(BlobHolder.class).getBlob());
            Blob blob = (Blob) as.run(oc, definition.getOperationChain());
            List<Blob> blobs = new ArrayList<Blob>();
            blobs.add(blob);
            return blobs;

        } catch (Exception e) {
            throw new RenditionException(
                    "Exception while running the operation chain: "
                            + definition.getOperationChain(), e);
        }
    }
}
