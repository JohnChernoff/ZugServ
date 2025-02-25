package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;

import java.util.Arrays;
import java.util.List;

//TODO: add scopes to ObjectNode for client?!
/**
 * A JSONifier is an Object which can (partially or completely) serialize itself via its toJSON method
 */
@FunctionalInterface
public interface JSONifier {

    /**
     * Creates a JSON representation of the Class.
     * @param scopes list of scopes
     * @return a (partial or complete) JSON representation of the Class
     */
    ObjectNode toJSON(List<String> scopes);

    default ObjectNode toJSON() {
        return toJSON(ZugScope.all);
    }

    default ObjectNode toJSON(String... scopes) {
        if (scopes == null || scopes.length == 0) return toJSON(ZugScope.all);
        else return toJSON(Arrays.stream(scopes).toList());
    }

    default ObjectNode toJSON(Enum<?>... scopes) {
        if (scopes == null || scopes.length == 0) return toJSON(ZugScope.all);
        else return toJSON(Arrays.stream(scopes).map(Enum::name).toList());
    }

    /**
     * Incidates if a given scope is "basic", i.e., part of the base ZugServ framework
     * @param scopes list of scopes
     * @return true if scope is ZugScope.basic or ZugScope.all
     */
    default boolean isBasic(List<String> scopes) {
        return scopes.contains(ZugScope.all.name()) || scopes.contains(ZugScope.basic.name());
    }

    /**
     * Incidates if a given scope in contained in the scopelist
     * @param scopes list of scopes
     * @param scope scope to check
     * @return true if scope is in list or scope contains ZugScope.all and there doesn't exist an excluding field ("!" plus the fieldname)
     */
    default boolean hasScope(List<String> scopes, String scope) {
        if (scopes.contains("!" + scope)) return false;
        else return scopes.contains(ZugScope.all.name()) || scopes.contains(scope);
    }

    default boolean hasScope(List<String> scopes, Enum<?> scope) {
        return hasScope(scopes, scope.name());
    }

    default boolean hasScope(List<String> scopes, String scope, boolean ignoreAll) {
        if (ignoreAll) return scopes.contains(scope); else return hasScope(scopes, scope);
    }

    default boolean hasScope(List<String> scopes, Enum<?> scope, boolean ignoreAll) {
        if (ignoreAll) return scopes.contains(scope.name()); else return hasScope(scopes, scope);
    }

    default String exclude(Enum<?> scope) {
        return "!" + scope.name();
    }
}
