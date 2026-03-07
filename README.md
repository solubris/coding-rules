
# TODO create syntactic sugar library

# TODO's
# ${project.version} can be excluded from the enforcer rule as it is a special property that is commonly used
# in fact, ${project.*} can be excluded

Split the enforcer into two rules:
- one for checking the existing properties
  - is the property used more than once?
    - if not, it must be removed
  - extract all artifacts from model
    - versionUsages = group by version, ignoring explicit properties
  - for each property:
    - versionUsages.get(property.name).size() -> usages of the property
      - if usages==1, then the property should be inlined and removed (ALLOW_SINGLE_USE_PROPERTIES=false)
        - please replace usgae of 'property.name' with 'property.value'
      - if usages==0, then the property should be removed (ALLOW_UNUSED_PROPERTIES=false)
        - please remove 'property.name'
    - what about if there is a version that is the same as the property value but not using the property?
      - This should be replaced with the property as well
      - and same rules apply to this version as well
      - so if usage==1, but would be usage==2 if we replace it with the property, then we should replace it with the property and keep the property
      - this means that versionUsages must capture both explicit and implicit usages of the version
      - perhaps the artifact model should cover both these cases
        - getVersion() returns the resolved version
          - how to get the resolved version?
            - perhaps this can come from the effective model
            - how populate the resolved version?
            - at time of building artifact model or as an enrichment step after building the artifact model?
        - getVersionProperty() returns the property if it exists, otherwise null

- one for checking repeated versions
  - if a version is repeated, it must be replaced with a property
  - extract all artifacts from model
  - resolve all versions to their effective versions
  - group by effectiveVersion
    - this ensures that property usages are grouped together with non-property usages
    - but only for dependencies in the original model
    - for each version:
      - if usages>1
        - if all usages are through a property - nothing to do
        - if some usages are through a property and some are not - replace all non-property usages
        - if no usages are through a property - create a new property and replace all usages with the property
      - if usages==1
        - if usage is through a property - remove the property (ALLOW_SINGLE_USE_PROPERTIES=false)
        - if usage is not through a property - nothing to do

Should these be two separate rules or one rule that handles both cases?
what would they be called?
- VersionPropertyEnforcerRule
  - check the properties and their usages
  - versionPropertySuffix = "version" (configurable)
- VersionUsageEnforcerRule
  - check the versions and their usages

ALLOW_SINGLE_USE_PROPERTIES - applicable to both rules

Feels like these rules are intrinsically linked and should be part of the same rule,
as they both deal with the same concept of properties and their usages.

Even so, can ensure the implementation is cleanly separated within the same rule

## PMD - collect to set should normally be replace with toUnmodifiableSet(), toUnmodifiableMap(), toUnmodifiableList()

especially when the result is assigned to a final field or returned from a method

eg:
                .collect(Collectors.toSet());
replace with:
                .collect(toUnmodifiableSet());

What about this case:
                .collect(groupingBy(Artifact::fullKey, toList()));

