package org.chernovia.lib.zugserv;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.enums.ZugScope;
import java.util.Arrays;
import java.util.List;

//TODO: add scopes to ObjectNode for client?!
/**
 * A JSONifier is an Object which can (partially or completely) serialize itself via its toJSON method
 */
public interface JSONifier {

    public final String EXCLUDE_PFX = "exclude_";

    /**
     * Creates a JSON representation of the Class.
     * @param scopes list of scopes
     * @return a (partial or complete) JSON representation of the Class
     */
    ObjectNode toJSON2(Enum<?>... scopes);

    /**
     * Creates a JSON representation of the Class.
     * @return toJSON2(ZugScope.all)
     */
    default ObjectNode toJSON() {
        return toJSON2(ZugScope.all);
    };
    //default ObjectNode toJSON() { ZugManager.log("Warning: use of depreciated toJson()"); new Exception().printStackTrace(); return toJSON(ZugScope.all); };

    /**
     * Incidates if a given scope is "basic", i.e., part of the base ZugServ framework
     * @param scopes list of scopes
     * @return true if scope is ZugScope.basic or ZugScope.all
     */
    default boolean isBasic(Enum<?>... scopes) {
        List<Enum<?>> scopeList = Arrays.stream(scopes).toList();
        return scopeList.contains(ZugScope.all) || scopeList.contains(ZugScope.basic); //could just use hasScope?
    }

    default String excludedEnum(Enum<?> e) {
        return EXCLUDE_PFX + e.name();
    }

    /**
     * Incidates if a given scope in contained in the scopelist
     * @param scopes list of scopes
     * @param scope scope to check
     * @return true if scope is in list or scope contains ZugScope.all and there doesn't exist an excluding field ("exclude_" plus the fieldname)
     */
    default boolean hasScope(Enum<?> scope, Enum<?>... scopes) {
        return hasScope(scope,false,scopes);
    }

    default boolean hasScope(Enum<?> scope, boolean ignoreAll,  Enum<?>... scopes) {
        if (ignoreAll) return Arrays.stream(scopes).toList().contains(scope);
        else return Arrays.stream(scopes).anyMatch(s -> s == ZugScope.all || s == scope);
    }

    default boolean hasExcludedScope(Enum<?> scope, Enum<?>... scopes) {
        return (Arrays.stream(scopes).map(Enum::name).toList().contains(excludedEnum(scope)));
    }

}
