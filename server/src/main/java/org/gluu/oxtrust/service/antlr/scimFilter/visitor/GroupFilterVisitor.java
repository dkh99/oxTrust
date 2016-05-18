/*
 * oxTrust is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */
package org.gluu.oxtrust.service.antlr.scimFilter.visitor;

import org.antlr.v4.runtime.tree.ParseTree;
import org.gluu.oxtrust.model.scim2.*;
import org.gluu.oxtrust.service.antlr.scimFilter.MainScimFilterVisitor;
import org.gluu.oxtrust.service.antlr.scimFilter.antlr4.ScimFilterParser;
import org.gluu.oxtrust.service.antlr.scimFilter.enums.ScimOperator;
import org.gluu.oxtrust.service.antlr.scimFilter.util.FilterUtil;
import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Val Pecaoco
 */
public class GroupFilterVisitor extends MainScimFilterVisitor {

    private Logger logger = LoggerFactory.getLogger(GroupFilterVisitor.class);

    private static Class[] annotatedClasses = { Resource.class, Group.class };

    private static Map<String, String> declaredAnnotations = new HashMap<String, String>();

    static {

        for (Class clazz : annotatedClasses) {

            for (Field nameField : clazz.getDeclaredFields()) {

                for (Annotation annotation : nameField.getDeclaredAnnotations()) {

                    if (annotation instanceof LdapAttribute) {
                        LdapAttribute ldapAttribute = (LdapAttribute)annotation;
                        declaredAnnotations.put(nameField.getName(), ldapAttribute.name());
                    }
                }
            }
        }
    }

    public static String getGroupLdapAttributeName(String attrName) {

        String ldapAttributeName = FilterUtil.stripScimSchema(Constants.GROUP_CORE_SCHEMA_ID, attrName);

        String[] tokens = ldapAttributeName.split("\\.");

        // This is already specific implementation. Currently only support up to second level.
        ldapAttributeName = tokens[0];
        if (tokens[0].equalsIgnoreCase(Name.class.getSimpleName())) {
            if (tokens.length == 1) {
                ldapAttributeName = "inum";
            } else if (tokens.length == 2) {
                ldapAttributeName = tokens[1];
            }
        }

        if (ldapAttributeName != null && !ldapAttributeName.isEmpty()) {

            for (Map.Entry<String, String> entry : declaredAnnotations.entrySet()) {

                if (ldapAttributeName.equalsIgnoreCase(entry.getKey())) {
                    ldapAttributeName = entry.getValue();
                    break;
                }
            }
        }

        return ldapAttributeName;
    }

    private String attrOperCriteriaResolver(String attrName, String operator, String criteria) {

        logger.info(" GroupFilterVisitor.attrOperCriteriaResolver() ");

        attrName = FilterUtil.stripScimSchema(Constants.GROUP_CORE_SCHEMA_ID, attrName);

        String[] tokens = attrName.split("\\.");

        String ldapAttributeName = getGroupLdapAttributeName(attrName);

        // This is already specific implementation. Currently only support up to second level.
        if (tokens.length == 2 && !tokens[0].equalsIgnoreCase(Name.class.getSimpleName())) {

            StringBuilder sb = new StringBuilder();

            if (ScimOperator.getByValue(operator.toLowerCase()).equals(ScimOperator.ENDS_WITH) ||
                    ScimOperator.getByValue(operator.toLowerCase()).equals(ScimOperator.CONTAINS)) {
                sb.append("\"");
            } else {
                sb.append("*\"");
            }

            sb.append(tokens[1]);

            if (ScimOperator.getByValue(operator.toLowerCase()).equals(ScimOperator.EQUAL)) {
                sb.append("\":\"");
            } else {
                sb.append("\":*");
            }

            sb.append(criteria);

            if (!(ScimOperator.getByValue(operator.toLowerCase()).equals(ScimOperator.STARTS_WITH) ||
                    ScimOperator.getByValue(operator.toLowerCase()).equals(ScimOperator.CONTAINS))) {
                sb.append("\"*");
            }

            criteria = sb.toString();
        }

        logger.info(" ##### attrName = " + attrName + ", ldapAttributeName = " + ldapAttributeName + ", criteria = " + criteria);

        String expr = ScimOperator.transform(operator, ldapAttributeName, criteria);
        logger.info(" ##### expr = " + expr);

        return expr;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public String visitATTR_OPER_CRITERIA(ScimFilterParser.ATTR_OPER_CRITERIAContext ctx) {

        logger.info(" GroupFilterVisitor.visitATTR_OPER_CRITERIA() ");

        ParseTree parent = ctx.getParent();
        while (parent != null) {

            if (parent.getClass().getSimpleName().equalsIgnoreCase(ScimFilterParser.LBRAC_EXPR_RBRACContext.class.getSimpleName())) {

                logger.info("********** PARENT = " + parent.getClass().getSimpleName());

                String attrName = ((ScimFilterParser.LBRAC_EXPR_RBRACContext)parent).ATTRNAME() + "." + ctx.ATTRNAME().getText();
                return attrOperCriteriaResolver(attrName, visit(ctx.operator()), visit(ctx.criteria()));

            } else {
                parent = parent.getParent();
            }
        }

        return attrOperCriteriaResolver(ctx.ATTRNAME().getText(), visit(ctx.operator()), visit(ctx.criteria()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public String visitATTR_OPER_EXPR(ScimFilterParser.ATTR_OPER_EXPRContext ctx) {

        logger.info(" GroupFilterVisitor.visitATTR_OPER_EXPR() ");

        ParseTree parent = ctx.getParent();
        while (parent != null) {

            if (parent.getClass().getSimpleName().equalsIgnoreCase(ScimFilterParser.LBRAC_EXPR_RBRACContext.class.getSimpleName())) {

                logger.info("********** PARENT = " + parent.getClass().getSimpleName());

                String attrName = ((ScimFilterParser.LBRAC_EXPR_RBRACContext)parent).ATTRNAME() + "." + ctx.ATTRNAME().getText();
                return attrOperCriteriaResolver(attrName, visit(ctx.operator()), visit(ctx.expression()));

            } else {
                parent = parent.getParent();
            }
        }

        return attrOperCriteriaResolver(ctx.ATTRNAME().getText(), visit(ctx.operator()), visit(ctx.expression()));
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public String visitATTR_PR(ScimFilterParser.ATTR_PRContext ctx) {

        logger.info(" GroupFilterVisitor.visitATTR_PR() ");

        String attrName = ctx.ATTRNAME().getText();
        attrName = FilterUtil.stripScimSchema(Constants.GROUP_CORE_SCHEMA_ID, attrName);
        String[] tokens = attrName.split("\\.");

        String ldapAttributeName = getGroupLdapAttributeName(attrName);

        StringBuilder result = new StringBuilder("");

        // This is already specific implementation. Currently only support up to second level.
        if (tokens.length == 2 && !tokens[0].equalsIgnoreCase(Name.class.getSimpleName())) {

            result.append("&(");
            result.append(ldapAttributeName);
            result.append("=*");
            result.append(")(");

            result.append(ldapAttributeName);
            result.append("=*\"");
            result.append(tokens[1]);
            result.append("\":\"*");
            result.append(")");

        } else {

            result.append(ldapAttributeName);
            result.append("=*");
        }

        logger.info(" ##### ATTRNAME = " + ctx.ATTRNAME().getText() + ", ldapAttributeName = " + ldapAttributeName);

        String expr = result.toString();
        logger.info(" ##### expr = " + expr);

        return expr;
    }

    /**
     * {@inheritDoc}
     * <p>
     * <p>The default implementation returns the result of calling
     * {@link #visitChildren} on {@code ctx}.</p>
     *
     * @param ctx
     */
    @Override
    public String visitLBRAC_EXPR_RBRAC(ScimFilterParser.LBRAC_EXPR_RBRACContext ctx) {

        logger.info(" GroupFilterVisitor.visitLBRAC_EXPR_RBRAC() ");

        StringBuilder result = new StringBuilder("");
        // result.append("&");
        // result.append("(");
        // result.append(getGroupLdapAttributeName(ctx.ATTRNAME().getText()));
        // result.append("=*");
        // result.append(")");
        // result.append("(");
        result.append(visit(ctx.expression()));  // See this.visitATTR_OPER_CRITERIA()
        // result.append(")");

        return result.toString();
    }
}