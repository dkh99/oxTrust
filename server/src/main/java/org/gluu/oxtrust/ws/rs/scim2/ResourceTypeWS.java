/*
 * oxTrust is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2015, Gluu
 */
package org.gluu.oxtrust.ws.rs.scim2;

import static org.gluu.oxtrust.model.scim2.Constants.MEDIA_TYPE_SCIM_JSON;
import static org.gluu.oxtrust.model.scim2.Constants.UTF8_CHARSET_FRAGMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.gluu.oxtrust.model.scim2.BaseScimResource;
import org.gluu.oxtrust.model.scim2.ListResponse;
import org.gluu.oxtrust.model.scim2.Meta;
import org.gluu.oxtrust.model.scim2.annotations.Schema;
import org.gluu.oxtrust.model.scim2.extensions.Extension;
import org.gluu.oxtrust.model.scim2.fido.FidoDeviceResource;
import org.gluu.oxtrust.model.scim2.group.GroupResource;
import org.gluu.oxtrust.model.scim2.provider.resourcetypes.ResourceType;
import org.gluu.oxtrust.model.scim2.provider.resourcetypes.SchemaExtensionHolder;
import org.gluu.oxtrust.model.scim2.user.UserResource;
import org.gluu.oxtrust.model.scim2.util.ScimResourceUtil;
import org.gluu.oxtrust.service.scim2.ExtensionService;
import org.gluu.oxtrust.service.scim2.interceptor.RejectFilterParam;

import com.wordnik.swagger.annotations.Api;

/**
 * @author Rahat Ali Date: 05.08.2015
 * Re-engineered by jgomer on 2017-09-25.
 */
@Named("resourceTypesWs")
@Path("/scim/v2/ResourceTypes")
@Api(value = "/v2/ResourceTypes", description = "SCIM 2.0 ResourceType Endpoint (https://tools.ietf.org/html/rfc7643#section-6)")
public class ResourceTypeWS extends BaseScimWebService {

    //The following are not computed using the endpointUrl's of web services since they are required to be constant (used in @Path annotations)
    private static final String USER_SUFFIX="User";
    private static final String GROUP_SUFFIX="Group";
    private static final String FIDO_SUFFIX="FidoDevice";

    @Inject
    private UserWebService userService;

    @Inject
    private GroupWebService groupService;

    @Inject
    private FidoDeviceWebService fidoService;

    @Inject
    private ExtensionService extService;

    @GET
    @Produces(MEDIA_TYPE_SCIM_JSON + UTF8_CHARSET_FRAGMENT)
    @HeaderParam("Accept") @DefaultValue(MEDIA_TYPE_SCIM_JSON)
    @RejectFilterParam
    public Response serve() {

        try {
            ListResponse listResponse = new ListResponse(1, 3, 3);
            listResponse.addResource(getUserResourceType());
            listResponse.addResource(getGroupResourceType());
            listResponse.addResource(getFidoDeviceResourceType());

            String json = resourceSerializer.getListResponseMapper().writeValueAsString(listResponse);
            return Response.ok(json).location(new URI(endpointUrl)).build();
        }
        catch (Exception e){
            log.error(e.getMessage(), e);
            return getErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }

    }

    @Path(USER_SUFFIX)
    @GET
    @Produces(MEDIA_TYPE_SCIM_JSON + UTF8_CHARSET_FRAGMENT)
    @HeaderParam("Accept") @DefaultValue(MEDIA_TYPE_SCIM_JSON)
    @RejectFilterParam
    public Response userResourceType() {

        try {
            URI uri=new URI(getResourceLocation(USER_SUFFIX));
            return Response.ok(resourceSerializer.serialize(getUserResourceType())).location(uri).build();
        }
        catch (Exception e){
            log.error("Failure at userResourceType method", e);
            return getErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }

    }

    @Path(GROUP_SUFFIX)
    @GET
    @Produces(MEDIA_TYPE_SCIM_JSON + UTF8_CHARSET_FRAGMENT)
    @HeaderParam("Accept") @DefaultValue(MEDIA_TYPE_SCIM_JSON)
    @RejectFilterParam
    public Response groupResourceType() {

        try {
            URI uri=new URI(getResourceLocation(GROUP_SUFFIX));
            return Response.ok(resourceSerializer.serialize(getGroupResourceType())).location(uri).build();
        }
        catch (Exception e){
            log.error("Failure at groupResourceType method", e);
            return getErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }

    }

    @Path(FIDO_SUFFIX)
    @GET
    @Produces(MEDIA_TYPE_SCIM_JSON + UTF8_CHARSET_FRAGMENT)
    @HeaderParam("Accept") @DefaultValue(MEDIA_TYPE_SCIM_JSON)
    @RejectFilterParam
    public Response fidoResourceType() {
        try {
            URI uri=new URI(getResourceLocation(FIDO_SUFFIX));
            return Response.ok(resourceSerializer.serialize(getFidoDeviceResourceType())).location(uri).build();
        }
        catch (Exception e){
            log.error("Failure at fidoResourceType method", e);
            return getErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }

    }

    @PostConstruct
    public void setup(){
        //weld makes you cry if using getClass() here
        endpointUrl=appConfiguration.getBaseEndpoint() + ResourceTypeWS.class.getAnnotation(Path.class).value();
    }

    private void fillResourceType(ResourceType rt, Schema schemaAnnot, String endpointUrl, String location, List<SchemaExtensionHolder> schemaExtensions){

        rt.setId(schemaAnnot.name());
        rt.setName(schemaAnnot.name());
        rt.setDescription(schemaAnnot.description());
        rt.setEndpoint(endpointUrl.substring(appConfiguration.getBaseEndpoint().length()));
        rt.setSchema(schemaAnnot.id());
        rt.setSchemaExtensions(schemaExtensions);

        Meta rtMeta = new Meta();
        rtMeta.setLocation(location);
        rtMeta.setResourceType("ResourceType");
        rt.setMeta(rtMeta);

    }

    private ResourceType getUserResourceType(){

        Class<? extends BaseScimResource> cls=UserResource.class;
        List<Extension> usrExtensions=extService.getResourceExtensions(cls);
        List<SchemaExtensionHolder> schemaExtensions=new ArrayList<SchemaExtensionHolder>();

        for (Extension extension : usrExtensions){
            SchemaExtensionHolder userExtensionSchema = new SchemaExtensionHolder();
            userExtensionSchema.setSchema(extension.getUrn());
            userExtensionSchema.setRequired(false);

            schemaExtensions.add(userExtensionSchema);
        }

        ResourceType usrRT=new ResourceType();
        fillResourceType(usrRT, ScimResourceUtil.getSchemaAnnotation(cls), userService.getEndpointUrl(), getResourceLocation(USER_SUFFIX), schemaExtensions);
        return usrRT;

    }

    private ResourceType getGroupResourceType(){
        ResourceType grRT=new ResourceType();
        fillResourceType(grRT, ScimResourceUtil.getSchemaAnnotation(GroupResource.class), groupService.getEndpointUrl(), getResourceLocation(GROUP_SUFFIX), null);
        return grRT;
    }

    private ResourceType getFidoDeviceResourceType(){
        ResourceType fidoRT =new ResourceType();
        fillResourceType(fidoRT, ScimResourceUtil.getSchemaAnnotation(FidoDeviceResource.class), fidoService.getEndpointUrl(), getResourceLocation(FIDO_SUFFIX),null);
        return fidoRT;
    }

    private String getResourceLocation(String suffix){
        return endpointUrl + "/" + suffix;
    }

}
