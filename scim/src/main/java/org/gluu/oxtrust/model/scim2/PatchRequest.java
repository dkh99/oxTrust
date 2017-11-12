package org.gluu.oxtrust.model.scim2;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.Collections;
import java.util.List;

import static org.gluu.oxtrust.model.scim2.Constants.PATCH_REQUEST_SCHEMA_ID;

/**
 * Created by jgomer on 2017-10-28.
 */
public class PatchRequest {

    private List<String> schemas;

    @JsonProperty("Operations")
    private List<PatchOperation> operations;

    public PatchRequest() {
        this.schemas = Collections.singletonList(PATCH_REQUEST_SCHEMA_ID);
    }

    public List<String> getSchemas() {
        return schemas;
    }

    public void setSchemas(List<String> schemas) {
        this.schemas = schemas;
    }

    public List<PatchOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<PatchOperation> operations) {
        this.operations = operations;
    }

}
