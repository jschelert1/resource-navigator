package com.jschelert.resourcenavigator.navigation

/**
 * =================================================================================================
 * ResourceOpenMode
 * =================================================================================================
 *
 * Resource Open Mode
 * ------------------
 * Enumerates the supported navigation modes used by Resource Navigator when
 * opening a resolved resource.
 *
 * Behavior
 * --------
 * • Determines how ResourceDispatcher opens a resource.
 * • Used by ResourceNavigationTargetFactory when constructing Choose
 *   Declaration navigation targets.
 * • Stored by ResourceNavigationElement until navigation is performed.
 *
 * Responsibilities
 * ----------------
 * • Represent the user's selected navigation action.
 * • Provide a type-safe navigation policy.
 *
 * Dependencies
 * ------------
 * • ResourceDispatcher
 * • ResourceNavigationElement
 * • ResourceNavigationTargetFactory
 *
 * See Also
 * --------
 * • ResourceDispatcher
 * • ResourceNavigationElement
 * • ResourceNavigationTargetFactory
 *
 * Architectural Notes
 * -------------------
 * • ResourceOpenMode intentionally contains only navigation policies.
 *   Decisions regarding which modes are available for a particular resource
 *   are made by ResourceNavigationTargetFactory, while execution of those
 *   modes is delegated to ResourceDispatcher.
 */
enum class ResourceOpenMode {

    /**
     * Open the resource within the IDE editor.
     */
    IDE,

    /**
     * Open the resource using the operating system's default application.
     */
    EXTERNAL,

    /**
     * Open the resource in the system web browser.
     */
    BROWSER,
}