rootProject.name = "AliucordPlugins"

// This file sets what projects are included. Every time you add a new project, you must add it
// to the includes below.

// Plugins are included like this
include(
    "Find",
    "Whois",
    "SnowflakeUtilities"
)

// This is required because dev.nope.plugins are in the ExamplePlugins/kotlin subdirectory.
//
// Assuming you put all your dev.nope.plugins into the project root, so on the same
// level as this file, simply remove everything below.
//
// Otherwise, if you want a different structure, for example all dev.nope.plugins in a folder called "dev.nope.plugins",
// then simply change the path
/*
rootProject.children.forEach {
    // Change kotlin to java if you'd rather use java
    it.projectDir = file("ExamplePlugins/kotlin/${it.name}")
}*/
