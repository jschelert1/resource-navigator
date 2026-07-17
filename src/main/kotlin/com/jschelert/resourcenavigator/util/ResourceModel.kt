package com.jschelert.resourcenavigator.util

import com.intellij.openapi.vfs.VirtualFile

/**
 * =================================================================================================
 * ResourceModel
 * =================================================================================================
 *
 * Resource Model
 * --------------
 * Defines the core data structures used to represent resolved resources within
 * Resource Navigator.
 *
 * Behavior
 * --------
 * • Represents either a local filesystem resource or an HTTP/HTTPS URL.
 * • Carries the original resource string together with its resolved metadata.
 * • Stores VirtualFile information for local resources when available.
 * • Indicates whether a local resource currently exists.
 *
 * Responsibilities
 * ----------------
 * • Provide immutable resource model objects.
 * • Represent resource kind.
 * • Transport resolver results between subsystems.
 *
 * Dependencies
 * ------------
 * • VirtualFile
 *
 * See Also
 * --------
 * • ResourceResolver
 * • ResourceClassifier
 * • ResourceDispatcher
 * • ResourceReferenceLocal
 * • ResourceReferenceUrl
 *
 * Architectural Notes
 * -------------------
 * • These model types intentionally contain no business logic.
 *
 * • ResourceTarget is produced by ResourceResolver and consumed throughout the
 *   navigation subsystem.
 *
 * • Keeping this model immutable simplifies reasoning about resource state and
 *   prevents accidental mutation after resolution.
 */

/**
 * Enumeration of supported resource categories.
 */
enum class ResourceKind {

    /**
     * A local filesystem resource.
     */
    LOCAL_FILE,

    /**
     * An HTTP or HTTPS URL.
     */
    URL,
}

/**
 * Immutable description of a resolved resource.
 *
 * @property rawValue
 * Original resource string extracted from the Python source.
 *
 * @property kind
 * Category of resolved resource.
 *
 * @property virtualFile
 * IntelliJ VirtualFile corresponding to the resolved local resource, or null
 * for URLs and unresolved local resources.
 *
 * @property resolvedPath
 * Fully resolved filesystem path, when applicable.
 *
 * @property exists
 * True if the local resource currently exists.
 */
data class ResourceTarget(

    val rawValue: String,

    val kind: ResourceKind,

    val virtualFile: VirtualFile? = null,

    val resolvedPath: String? = null,

    val exists: Boolean = virtualFile != null,
)