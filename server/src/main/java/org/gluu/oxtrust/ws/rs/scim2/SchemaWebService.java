/*
 * oxTrust is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.gluu.oxtrust.ws.rs.scim2;

import static org.gluu.oxtrust.model.scim2.Constants.MEDIA_TYPE_SCIM_JSON;
import static org.gluu.oxtrust.model.scim2.Constants.UTF8_CHARSET_FRAGMENT;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.inject.Named;
import javax.lang.model.type.NullType;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.annotate.JsonProperty;
import org.gluu.oxtrust.model.scim2.AttributeDefinition;
import org.gluu.oxtrust.model.scim2.BaseScimResource;
import org.gluu.oxtrust.model.scim2.ListResponse;
import org.gluu.oxtrust.model.scim2.Meta;
import org.gluu.oxtrust.model.scim2.annotations.Attribute;
import org.gluu.oxtrust.model.scim2.annotations.Schema;
import org.gluu.oxtrust.model.scim2.extensions.Extension;
import org.gluu.oxtrust.model.scim2.extensions.ExtensionField;
import org.gluu.oxtrust.model.scim2.provider.config.ServiceProviderConfig;
import org.gluu.oxtrust.model.scim2.provider.resourcetypes.ResourceType;
import org.gluu.oxtrust.model.scim2.provider.schema.SchemaAttribute;
import org.gluu.oxtrust.model.scim2.provider.schema.SchemaResource;
import org.gluu.oxtrust.model.scim2.util.IntrospectUtil;
import org.gluu.oxtrust.model.scim2.util.ScimResourceUtil;
import org.gluu.oxtrust.service.scim2.interceptor.RejectFilterParam;

import com.wordnik.swagger.annotations.Api;

/**
 * Web service for the /Schemas endpoint.
 *
 * @author Val Pecaoco
 * Re-engineered by jgomer on 2017-09-27.
 */
@Named("scim2SchemaEndpoint")
@Path("/scim/v2/Schemas")
@Api(value = "/v2/Schemas", description = "SCIM 2.0 Schema Endpoint (https://tools.ietf.org/html/rfc7643#section-4)")
public class SchemaWebService extends BaseScimWebService {

    private Map<String, Class<? extends BaseScimResource>> resourceSchemas;

    @GET
    @Produces(MEDIA_TYPE_SCIM_JSON + UTF8_CHARSET_FRAGMENT)
    @HeaderParam("Accept") @DefaultValue(MEDIA_TYPE_SCIM_JSON)
    @RejectFilterParam
    public Response serve(){

        Response response;
        try {
            int total = resourceSchemas.size();
            ListResponse listResponse = new ListResponse(1, total, total);

            for (String urn : resourceSchemas.keySet()){
                listResponse.addResource(getSchemaInstance(resourceSchemas.get(urn), urn));
            }
            String json=resourceSerializer.getListResponseMapper().writeValueAsString(listResponse);
            response=Response.ok(json).location(new URI(endpointUrl)).build();
        }
        catch (Exception e){
            log.error("Failure at serve method", e);
            response=getErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
        return response;
    }

    @GET
    @Path("{schemaUrn}")
    @Produces(MEDIA_TYPE_SCIM_JSON + UTF8_CHARSET_FRAGMENT)
    @HeaderParam("Accept") @DefaultValue(MEDIA_TYPE_SCIM_JSON)
    @RejectFilterParam
    public Response getSchemaById(@PathParam("schemaUrn") String urn){

        Response response;
        try {
            Class<? extends BaseScimResource> cls = resourceSchemas.get(urn);

            if (cls==null){
                log.info("Schema urn {} not recognized", urn);
                response=Response.status(Response.Status.NOT_FOUND).build();
            }
            else {
                String json=resourceSerializer.serialize(getSchemaInstance(cls, urn));
                URI location = new URI(endpointUrl + "/" + urn);
                response = Response.ok(json).location(location).build();
            }
        }
        catch (Exception e){
            log.error("Failure at getSchemaById method", e);
            response=getErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
        return response;
    }

    @PostConstruct
    public void setup() {
        //Do not use getClass() here... a typical weld issue...
        endpointUrl = appConfiguration.getBaseEndpoint() + SchemaWebService.class.getAnnotation(Path.class).value();

        List<Class<? extends BaseScimResource>> excludedResources=Arrays.asList(SchemaResource.class, ResourceType.class, ServiceProviderConfig.class);
        resourceSchemas=new HashMap<String, Class<? extends BaseScimResource>>();

        //Fill map with urn vs. resource
        for (Class<? extends BaseScimResource> cls : IntrospectUtil.allAttrs.keySet()){
            if (!excludedResources.contains(cls)) {
                resourceSchemas.put(ScimResourceUtil.getDefaultSchemaUrn(cls), cls);

                for (Extension extension : extService.getResourceExtensions(cls))
                    resourceSchemas.put(extension.getUrn(), cls);
            }
        }

    }

    private SchemaResource getSchemaInstance(Class<? extends BaseScimResource> clazz) throws Exception{

        SchemaResource resource;
        Class<? extends BaseScimResource> schemaCls=SchemaResource.class;
        Schema annotation=ScimResourceUtil.getSchemaAnnotation(clazz);
        if (!clazz.equals(schemaCls) && annotation!=null){

            Meta meta=new Meta();
            meta.setResourceType(ScimResourceUtil.getType(schemaCls));
            meta.setLocation(endpointUrl + "/" + annotation.id());

            resource=new SchemaResource();
            resource.setId(annotation.id());
            resource.setName(annotation.name());
            resource.setDescription(annotation.description());
            resource.setMeta(meta);

            List<SchemaAttribute> attribs=new ArrayList<SchemaAttribute>();
            //paths are, happily alphabetically sorted :)
            for (String path : IntrospectUtil.allAttrs.get(clazz)){
                SchemaAttribute schAttr=new SchemaAttribute();
                Field f=IntrospectUtil.findFieldFromPath(clazz, path);

                Attribute attrAnnot=f.getAnnotation(Attribute.class);
                if (attrAnnot!=null) {
                    JsonProperty jsonAnnot=f.getAnnotation(JsonProperty.class);

                    schAttr.setName(jsonAnnot==null ? f.getName() : jsonAnnot.value());
                    schAttr.setType(attrAnnot.type().getName());
                    schAttr.setMultiValued(!attrAnnot.multiValueClass().equals(NullType.class) || IntrospectUtil.isCollection(f.getType()));
                    schAttr.setDescription(attrAnnot.description());
                    schAttr.setRequired(attrAnnot.isRequired());

                    schAttr.setCanonicalValues(attrAnnot.canonicalValues().length==0 ? null : Arrays.asList(attrAnnot.canonicalValues()));
                    schAttr.setCaseExact(attrAnnot.isCaseExact());
                    schAttr.setMutability(attrAnnot.mutability().getName());
                    schAttr.setReturned(attrAnnot.returned().getName());
                    schAttr.setUniqueness(attrAnnot.uniqueness().getName());
                    schAttr.setReferenceTypes(attrAnnot.referenceTypes().length==0 ? null : Arrays.asList(attrAnnot.referenceTypes()));

                    if (attrAnnot.type().equals(AttributeDefinition.Type.COMPLEX))
                        schAttr.setSubAttributes(new ArrayList<SchemaAttribute>());

                    List<SchemaAttribute> list=attribs;     //root list
                    String parts[]=path.split("\\.");

                    for (int i=0;i<parts.length-1;i++) {    //skip last part (real attribute name)
                        int j = list.indexOf(new SchemaAttribute(parts[i]));
                        list = list.get(j).getSubAttributes();
                    }

                    list.add(schAttr);
                }
            }
            resource.setAttributes(attribs);
        }
        else
            resource=null;

        return resource;
    }

    private SchemaResource getSchemaInstance(Class<? extends BaseScimResource> clazz, String urn) throws Exception{

        if (ScimResourceUtil.getDefaultSchemaUrn(clazz).equals(urn))
            return getSchemaInstance(clazz);    //Process core attributes
        else{   //process extension attributes
            SchemaResource resource=null;
            Class<? extends BaseScimResource> schemaCls=SchemaResource.class;

            //Find the appropriate extension
            List<Extension> extensions=extService.getResourceExtensions(clazz);
            for (Extension extension : extensions) {
                if (extension.getUrn().equals(urn)) {

                    Meta meta = new Meta();
                    meta.setResourceType(ScimResourceUtil.getType(schemaCls));
                    meta.setLocation(endpointUrl + "/" + urn);

                    resource = new SchemaResource();
                    resource.setId(urn);
                    resource.setName(extension.getName());
                    resource.setDescription(extension.getDescription());
                    resource.setMeta(meta);

                    List<SchemaAttribute> attribs = new ArrayList<SchemaAttribute>();

                    for (ExtensionField field : extension.getFields().values()) {
                        SchemaAttribute schAttr = new SchemaAttribute();

                        schAttr.setName(field.getName());
                        schAttr.setMultiValued(field.isMultiValued());
                        schAttr.setDescription(field.getDescription());
                        schAttr.setRequired(false);

                        schAttr.setCanonicalValues(null);
                        schAttr.setCaseExact(false);
                        schAttr.setMutability(AttributeDefinition.Mutability.READ_WRITE.getName());
                        schAttr.setReturned(AttributeDefinition.Returned.DEFAULT.getName());
                        schAttr.setUniqueness(AttributeDefinition.Uniqueness.NONE.getName());
                        schAttr.setReferenceTypes(null);

                        AttributeDefinition.Type type = field.getAttributeDefinitionType();
                        schAttr.setType(type==null ? null : type.getName());
                        schAttr.setSubAttributes(null);

                        attribs.add(schAttr);
                    }

                    resource.setAttributes(attribs);
                    break;
                }
            }
            return resource;
        }
    }

}
