package org.springframework.roo.addon.jpa.addon;

import static org.springframework.roo.model.JavaType.OBJECT;
import static org.springframework.roo.model.JpaJavaType.EMBEDDABLE;
import static org.springframework.roo.model.RooJavaType.ROO_EQUALS;
import static org.springframework.roo.model.RooJavaType.ROO_IDENTIFIER;
import static org.springframework.roo.model.RooJavaType.ROO_JAVA_BEAN;
import static org.springframework.roo.model.RooJavaType.ROO_SERIALIZABLE;
import static org.springframework.roo.model.RooJavaType.ROO_TO_STRING;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.application.config.ApplicationConfigService;
import org.springframework.roo.classpath.PhysicalTypeCategory;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.TypeManagementService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.JdkJavaType;
import org.springframework.roo.process.manager.FileManager;
import org.springframework.roo.project.Dependency;
import org.springframework.roo.project.FeatureNames;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.project.maven.Pom;
import org.springframework.roo.support.logging.HandlerUtils;
import org.springframework.roo.support.util.XmlUtils;
import org.w3c.dom.Element;

/**
 * Implementation of {@link JpaOperations}.
 * 
 * @author Stefan Schmidt
 * @author Alan Stewart
 * @author Juan Carlos García
 * @since 1.0
 */
@Component
@Service
public class JpaOperationsImpl implements JpaOperations {
	
	protected final static Logger LOGGER = HandlerUtils.getLogger(JpaOperationsImpl.class);
	
	// ------------ OSGi component attributes ----------------
   	private BundleContext context;
   	
    private static final String DATASOURCE_PREFIX = "spring.datasource";
    private static final String DATABASE_DRIVER = "driver-class-name";
    private static final String DATABASE_PASSWORD = "password";
    private static final String DATABASE_URL = "url";
    private static final String DATABASE_USERNAME = "username";
    private static final String JNDI_NAME = "jndi-name";
    static final String POM_XML = "pom.xml";

    private FileManager fileManager;
    private PathResolver pathResolver;
    private ProjectOperations projectOperations;
    private TypeLocationService typeLocationService;
    private TypeManagementService typeManagementService;
    private ApplicationConfigService applicationConfigService;
    
    protected void activate(final ComponentContext context) {
        this.context = context.getBundleContext();
    }

    @Override
    public void configureJpa(final OrmProvider ormProvider,
            final JdbcDatabase jdbcDatabase, final String jndi,
            final String hostName, final String databaseName,
            final String userName, final String password,
            final String moduleName, final String profile,
            final boolean force) {
    	
    	if(projectOperations == null){
    		projectOperations = getProjectOperations();
    	}
    	Validate.notNull(projectOperations, "ProjectOperations is required");
    	
        Validate.notNull(ormProvider, "ORM provider required");
        Validate.notNull(jdbcDatabase, "JDBC database required");

       // Parse the configuration.xml file
       final Element configuration = XmlUtils.getConfiguration(getClass());
        
        // Get the first part of the XPath expressions for unwanted databases
        // and ORM providers
        final String databaseXPath = getDbXPath(getUnwantedDatabases(jdbcDatabase));
        final String providersXPath = getProviderXPath(getUnwantedOrmProviders(ormProvider));
        
        // Updating pom.xml including necessary properties, dependencies and Spring Boot starters
        updateDependencies(configuration, ormProvider, jdbcDatabase,
                databaseXPath, providersXPath, moduleName);
        
        // Update Spring Config File with spring.datasource.* domain properties
        updateApplicationProperties(ormProvider, jdbcDatabase, hostName,
                databaseName, userName, password, moduleName, jndi, profile, force);
        
    }
    
    @Override
    public boolean isJpaInstallationPossible() {
        
        if(projectOperations == null){
            projectOperations = getProjectOperations();
        }
        Validate.notNull(projectOperations, "ProjectOperations is required");
        
        return projectOperations.isFocusedProjectAvailable();
    }
    
    @Override
    public void newEmbeddableClass(final JavaType name,
            final boolean serializable) {
        
        if(pathResolver == null){
            pathResolver = getPathResolver();
        }
        Validate.notNull(pathResolver, "PathResolver is required");
        
        if(typeManagementService == null){
            typeManagementService = getTypeManagementService();
        }
        Validate.notNull(typeManagementService, "TypeManagementService is required");
        
        Validate.notNull(name, "Embeddable name required");

        final String declaredByMetadataId = PhysicalTypeIdentifier
                .createIdentifier(name,
                        pathResolver.getFocusedPath(Path.SRC_MAIN_JAVA));

        final List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>(
                Arrays.asList(new AnnotationMetadataBuilder(ROO_JAVA_BEAN),
                        new AnnotationMetadataBuilder(ROO_TO_STRING),
                        new AnnotationMetadataBuilder(EMBEDDABLE)));

        if (serializable) {
            annotations.add(new AnnotationMetadataBuilder(ROO_SERIALIZABLE));
        }

        final int modifier = Modifier.PUBLIC;
        final ClassOrInterfaceTypeDetailsBuilder cidBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                declaredByMetadataId, modifier, name,
                PhysicalTypeCategory.CLASS);
        cidBuilder.setAnnotations(annotations);

        typeManagementService.createOrUpdateTypeOnDisk(cidBuilder.build());
    }
    
    @Override
    public void newEntity(final JavaType name, final boolean createAbstract,
            final JavaType superclass, final JavaType implementsType,
            final List<AnnotationMetadataBuilder> annotations) {
        
        if(pathResolver == null){
            pathResolver = getPathResolver();
        }
        Validate.notNull(pathResolver, "PathResolver is required");
        
        if(typeLocationService == null){
            typeLocationService = getTypeLocationService();
        }
        Validate.notNull(typeLocationService, "TypeLocationService is required");
        
        if(typeManagementService == null){
            typeManagementService = getTypeManagementService();
        }
        Validate.notNull(typeManagementService, "TypeManagementService is required");
        
        Validate.notNull(name, "Entity name required");
        Validate.isTrue(
                !JdkJavaType.isPartOfJavaLang(name.getSimpleTypeName()),
                "Entity name '%s' must not be part of java.lang",
                name.getSimpleTypeName());

        int modifier = Modifier.PUBLIC;
        if (createAbstract) {
            modifier |= Modifier.ABSTRACT;
        }

        final String declaredByMetadataId = PhysicalTypeIdentifier
                .createIdentifier(name,
                        pathResolver.getFocusedPath(Path.SRC_MAIN_JAVA));
        final ClassOrInterfaceTypeDetailsBuilder cidBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                declaredByMetadataId, modifier, name,
                PhysicalTypeCategory.CLASS);

        if (!superclass.equals(OBJECT)) {
            final ClassOrInterfaceTypeDetails superclassClassOrInterfaceTypeDetails = typeLocationService
                    .getTypeDetails(superclass);
            if (superclassClassOrInterfaceTypeDetails != null) {
                cidBuilder
                        .setSuperclass(new ClassOrInterfaceTypeDetailsBuilder(
                                superclassClassOrInterfaceTypeDetails));
            }
        }

        cidBuilder.setExtendsTypes(Arrays.asList(superclass));

        if (implementsType != null) {
            final Set<JavaType> implementsTypes = new LinkedHashSet<JavaType>();
            final ClassOrInterfaceTypeDetails typeDetails = typeLocationService
                    .getTypeDetails(declaredByMetadataId);
            if (typeDetails != null) {
                implementsTypes.addAll(typeDetails.getImplementsTypes());
            }
            implementsTypes.add(implementsType);
            cidBuilder.setImplementsTypes(implementsTypes);
        }

        cidBuilder.setAnnotations(annotations);

        typeManagementService.createOrUpdateTypeOnDisk(cidBuilder.build());
    }

    @Override
    public void newIdentifier(final JavaType identifierType,
            final String identifierField, final String identifierColumn) {
        
        if(pathResolver == null){
            pathResolver = getPathResolver();
        }
        Validate.notNull(pathResolver, "PathResolver is required");
        
        if(typeManagementService == null){
            typeManagementService = getTypeManagementService();
        }
        Validate.notNull(typeManagementService, "TypeManagementService is required");
        
        Validate.notNull(identifierType, "Identifier type required");

        final String declaredByMetadataId = PhysicalTypeIdentifier
                .createIdentifier(identifierType,
                        pathResolver.getFocusedPath(Path.SRC_MAIN_JAVA));
        final List<AnnotationMetadataBuilder> identifierAnnotations = Arrays
                .asList(new AnnotationMetadataBuilder(ROO_TO_STRING),
                        new AnnotationMetadataBuilder(ROO_EQUALS),
                        new AnnotationMetadataBuilder(ROO_IDENTIFIER));
        final ClassOrInterfaceTypeDetailsBuilder cidBuilder = new ClassOrInterfaceTypeDetailsBuilder(
                declaredByMetadataId, Modifier.PUBLIC | Modifier.FINAL,
                identifierType, PhysicalTypeCategory.CLASS);
        cidBuilder.setAnnotations(identifierAnnotations);

        typeManagementService.createOrUpdateTypeOnDisk(cidBuilder.build());
    }
    

    @Override
    public SortedSet<String> getDatabaseProperties(String profile) {
        
        if(projectOperations == null){
            projectOperations = getProjectOperations();
        }
        Validate.notNull(projectOperations, "ProjectOperations is required");
        
        return getApplicationConfigService().getPropertyKeys(DATASOURCE_PREFIX, true, profile);
    }
    
    @Override
    public boolean hasSpringDataDependency() {
        Pom pom = projectOperations.getFocusedModule();
        Dependency springDataDependency = new Dependency("org.springframework.boot", "spring-boot-starter-data-jpa", "");
        for(Dependency dependency : pom.getDependencies()){
            if(dependency.equals(springDataDependency)){
                return true;
            }
        }
        return false;
    }
    

    private String getConnectionString(final JdbcDatabase jdbcDatabase,
            String hostName, final String databaseName, final String moduleName) {
    	
    	if(projectOperations == null){
    		projectOperations = getProjectOperations();
    	}
    	Validate.notNull(projectOperations, "ProjectOperations is required");
    	
        String connectionString = jdbcDatabase.getConnectionString();
        if (connectionString.contains("TO_BE_CHANGED_BY_ADDON")) {
            connectionString = connectionString.replace(
                    "TO_BE_CHANGED_BY_ADDON", StringUtils
                            .isNotBlank(databaseName) ? databaseName
                            : projectOperations.getProjectName(moduleName));
        }
        else {
            if (StringUtils.isNotBlank(databaseName)) {
                // Oracle uses a different connection URL - see ROO-1203
                final String dbDelimiter = jdbcDatabase == JdbcDatabase.ORACLE ? ":"
                        : "/";
                connectionString += dbDelimiter + databaseName;
            }
        }
        if (StringUtils.isBlank(hostName)) {
            hostName = "localhost";
        }
        return connectionString.replace("HOST_NAME", hostName);
    }

    private String getDbXPath(final List<JdbcDatabase> databases) {
        final StringBuilder builder = new StringBuilder(
                "/configuration/databases/database[");
        for (int i = 0; i < databases.size(); i++) {
            if (i > 0) {
                builder.append(" or ");
            }
            builder.append("@id = '");
            builder.append(databases.get(i).getKey());
            builder.append("'");
        }
        builder.append("]");
        return builder.toString();
    }

    private String getProviderXPath(final List<OrmProvider> ormProviders) {
        final StringBuilder builder = new StringBuilder(
                "/configuration/ormProviders/provider[");
        for (int i = 0; i < ormProviders.size(); i++) {
            if (i > 0) {
                builder.append(" or ");
            }
            builder.append("@id = '");
            builder.append(ormProviders.get(i).name());
            builder.append("'");
        }
        builder.append("]");
        return builder.toString();
    }


    private List<JdbcDatabase> getUnwantedDatabases(
            final JdbcDatabase jdbcDatabase) {
        final List<JdbcDatabase> unwantedDatabases = new ArrayList<JdbcDatabase>();
        for (final JdbcDatabase database : JdbcDatabase.values()) {
            if (!database.getKey().equals(jdbcDatabase.getKey())
                    && !database.getDriverClassName().equals(
                            jdbcDatabase.getDriverClassName())) {
                unwantedDatabases.add(database);
            }
        }
        return unwantedDatabases;
    }

    private List<OrmProvider> getUnwantedOrmProviders(
            final OrmProvider ormProvider) {
        final List<OrmProvider> unwantedOrmProviders = new LinkedList<OrmProvider>(
                Arrays.asList(OrmProvider.values()));
        unwantedOrmProviders.remove(ormProvider);
        return unwantedOrmProviders;
    }

    public boolean hasDatabaseProperties() {
        SortedSet<String> databaseProperties = getApplicationConfigService()
                .getPropertyKeys(DATASOURCE_PREFIX, false, null);
    	
        return !databaseProperties.isEmpty();
    }

    private void updateApplicationProperties(final OrmProvider ormProvider,
            final JdbcDatabase jdbcDatabase, final String hostName,
            final String databaseName, String userName, final String password,
            final String moduleName, String jndi, String profile, boolean force) {
    	
        // Check if jndi is blank. If is blank, include database properties on 
        // application.properties file
        if(StringUtils.isBlank(jndi)){
            
            final String connectionString = getConnectionString(jdbcDatabase,
                    hostName, databaseName, moduleName);
            if (jdbcDatabase.getKey().equals("HYPERSONIC")
                    || jdbcDatabase == JdbcDatabase.H2_IN_MEMORY
                    || jdbcDatabase == JdbcDatabase.SYBASE) {
                userName = StringUtils.defaultIfEmpty(userName, "sa");
            }

            // Getting current properties
            final String driver = getApplicationConfigService().getProperty(DATASOURCE_PREFIX, DATABASE_DRIVER);
            final String url = getApplicationConfigService().getProperty(DATASOURCE_PREFIX, DATABASE_URL);
            final String uname = getApplicationConfigService().getProperty(DATASOURCE_PREFIX, DATABASE_USERNAME);
            final String pwd = getApplicationConfigService().getProperty(DATASOURCE_PREFIX, DATABASE_PASSWORD);

            boolean hasChanged = driver == null
                    || !driver.equals(jdbcDatabase.getDriverClassName());
            hasChanged |= url == null || !url.equals(connectionString);
            hasChanged |= uname == null
                    || !uname.equals(StringUtils.stripToEmpty(userName));
            hasChanged |= pwd == null
                    || !pwd.equals(StringUtils.stripToEmpty(password));
            if (!hasChanged) {
                // No changes from existing database configuration so exit now
                return;
            }
            
            // Write changes to Spring Config file
            Map<String, String> props = new HashMap<String, String>();
            props.put(DATABASE_URL, connectionString);
            props.put(DATABASE_DRIVER, jdbcDatabase.getDriverClassName());
            if(userName != null){
                props.put(DATABASE_USERNAME, StringUtils.stripToEmpty(userName));
            }
            if(password != null){
                props.put(DATABASE_PASSWORD, StringUtils.stripToEmpty(password));
            }
            
            getApplicationConfigService().addProperties(DATASOURCE_PREFIX, props, profile, force);

            // Remove jndi property
            getApplicationConfigService().removeProperty(DATASOURCE_PREFIX, JNDI_NAME, profile);
            
        }else{
            
            final String jndiProperty = getApplicationConfigService().getProperty(DATASOURCE_PREFIX, JNDI_NAME);
            
            boolean hasChanged = jndiProperty == null || 
                    !jndiProperty.equals(StringUtils.stripToEmpty(jndi));
            if (!hasChanged) {
                // No changes from existing database configuration so exit now
                return;
            }
            
            // Write changes to Spring Config file
            Map<String, String> props = new HashMap<String, String>();
            props.put(JNDI_NAME, jndi);
            
            getApplicationConfigService().addProperties(DATASOURCE_PREFIX, props, profile, force);
            
            // Remove old properties
            getApplicationConfigService().removeProperty(DATASOURCE_PREFIX, DATABASE_URL, profile);
            getApplicationConfigService().removeProperty(DATASOURCE_PREFIX, DATABASE_DRIVER, profile);
            getApplicationConfigService().removeProperty(DATASOURCE_PREFIX, DATABASE_USERNAME, profile);
            getApplicationConfigService().removeProperty(DATASOURCE_PREFIX, DATABASE_PASSWORD, profile);
            
        }
    }

    /**
     * Updates the POM with the dependencies required for the given database and
     * ORM provider, removing any other persistence-related dependencies
     * 
     * @param configuration
     * @param ormProvider
     * @param jdbcDatabase
     * @param databaseXPath
     * @param providersXPath
     */
    private void updateDependencies(final Element configuration,
            final OrmProvider ormProvider, final JdbcDatabase jdbcDatabase,
            final String databaseXPath, final String providersXPath,
            final String moduleName) {
    	
    	if(projectOperations == null){
    		projectOperations = getProjectOperations();
    	}
    	Validate.notNull(projectOperations, "ProjectOperations is required");
    	
        final List<Dependency> requiredDependencies = new ArrayList<Dependency>();

        final List<Element> databaseDependencies = XmlUtils.findElements(
                jdbcDatabase.getConfigPrefix() + "/dependencies/dependency",
                configuration);
        for (final Element dependencyElement : databaseDependencies) {
            requiredDependencies.add(new Dependency(dependencyElement));
        }

        final List<Element> ormDependencies = XmlUtils.findElements(
                ormProvider.getConfigPrefix() + "/dependencies/dependency",
                configuration);
        for (final Element dependencyElement : ormDependencies) {
            requiredDependencies.add(new Dependency(dependencyElement));
        }

        // Hard coded to JPA & Hibernate Validator for now
        final List<Element> jpaDependencies = XmlUtils
                .findElements(
                        "/configuration/persistence/provider[@id = 'JPA']/dependencies/dependency",
                        configuration);
        for (final Element dependencyElement : jpaDependencies) {
            requiredDependencies.add(new Dependency(dependencyElement));
        }

        final List<Element> springDependencies = XmlUtils.findElements(
                "/configuration/spring/dependencies/dependency", configuration);
        for (final Element dependencyElement : springDependencies) {
            requiredDependencies.add(new Dependency(dependencyElement));
        }

        // Remove redundant dependencies
        final List<Dependency> redundantDependencies = new ArrayList<Dependency>();
        redundantDependencies.addAll(getDependencies(databaseXPath,
                configuration, moduleName));
        redundantDependencies.addAll(getDependencies(providersXPath,
                configuration, moduleName));
        // Don't remove any we actually need
        redundantDependencies.removeAll(requiredDependencies);

        // Update the POM
        projectOperations.addDependencies(moduleName, requiredDependencies);
        projectOperations.removeDependencies(moduleName, redundantDependencies);
    }
    
    private List<Dependency> getDependencies(final String xPathExpression,
            final Element configuration, final String moduleName) {
        
        if(projectOperations == null){
            projectOperations = getProjectOperations();
        }
        Validate.notNull(projectOperations, "ProjectOperations is required");
        
        final List<Dependency> dependencies = new ArrayList<Dependency>();
        for (final Element dependencyElement : XmlUtils.findElements(
                xPathExpression + "/dependencies/dependency", configuration)) {
            final Dependency dependency = new Dependency(dependencyElement);
            if (dependency.getGroupId().equals("com.google.appengine")
                    && dependency.getArtifactId().equals(
                            "appengine-api-1.0-sdk")
                    && projectOperations
                            .isFeatureInstalledInFocusedModule(FeatureNames.GWT)) {
                continue;
            }
            dependencies.add(dependency);
        }
        return dependencies;
    }
    
    public FileManager getFileManager(){
    	// Get all Services implement FileManager interface
		try {
			ServiceReference<?>[] references = this.context.getAllServiceReferences(FileManager.class.getName(), null);
			
			for(ServiceReference<?> ref : references){
				return (FileManager) this.context.getService(ref);
			}
			
			return null;
			
		} catch (InvalidSyntaxException e) {
			LOGGER.warning("Cannot load FileManager on JpaOperationsImpl.");
			return null;
		}
    }
    
    public PathResolver getPathResolver(){
    	// Get all Services implement PathResolver interface
		try {
			ServiceReference<?>[] references = this.context.getAllServiceReferences(PathResolver.class.getName(), null);
			
			for(ServiceReference<?> ref : references){
				return (PathResolver) this.context.getService(ref);
			}
			
			return null;
			
		} catch (InvalidSyntaxException e) {
			LOGGER.warning("Cannot load PathResolver on JpaOperationsImpl.");
			return null;
		}
    }
    
    public ProjectOperations getProjectOperations(){
    	// Get all Services implement ProjectOperations interface
		try {
			ServiceReference<?>[] references = this.context.getAllServiceReferences(ProjectOperations.class.getName(), null);
			
			for(ServiceReference<?> ref : references){
				return (ProjectOperations) this.context.getService(ref);
			}
			
			return null;
			
		} catch (InvalidSyntaxException e) {
			LOGGER.warning("Cannot load ProjectOperations on JpaOperationsImpl.");
			return null;
		}
    }
    
    public ApplicationConfigService getApplicationConfigService(){
        if(applicationConfigService == null){
            // Get all Services implement ApplicationConfigService interface
            try {
                ServiceReference<?>[] references = this.context.getAllServiceReferences(ApplicationConfigService.class.getName(), null);
                
                for(ServiceReference<?> ref : references){
                    applicationConfigService = (ApplicationConfigService) this.context.getService(ref);
                }
                
                return null;
                
            } catch (InvalidSyntaxException e) {
                LOGGER.warning("Cannot load ApplicationConfigService on JpaOperationsImpl.");
                return null;
            }
        }else{
            return applicationConfigService;
        }

    }
    
    public TypeLocationService getTypeLocationService(){
    	// Get all Services implement TypeLocationService interface
		try {
			ServiceReference<?>[] references = this.context.getAllServiceReferences(TypeLocationService.class.getName(), null);
			
			for(ServiceReference<?> ref : references){
				return (TypeLocationService) this.context.getService(ref);
			}
			
			return null;
			
		} catch (InvalidSyntaxException e) {
			LOGGER.warning("Cannot load TypeLocationService on JpaOperationsImpl.");
			return null;
		}
    }
    
    public TypeManagementService getTypeManagementService(){
    	// Get all Services implement TypeManagementService interface
		try {
			ServiceReference<?>[] references = this.context.getAllServiceReferences(TypeManagementService.class.getName(), null);
			
			for(ServiceReference<?> ref : references){
				return (TypeManagementService) this.context.getService(ref);
			}
			
			return null;
			
		} catch (InvalidSyntaxException e) {
			LOGGER.warning("Cannot load TypeManagementService on JpaOperationsImpl.");
			return null;
		}
    }
    
    /**
     * FEATURE Methods
     */
    
    public boolean isInstalledInModule(final String moduleName) {
        
        if(fileManager == null){
            fileManager = getFileManager();
        }
        Validate.notNull(fileManager, "FileManager is required");
        
        if(pathResolver == null){
            pathResolver = getPathResolver();
        }
        Validate.notNull(pathResolver, "PathResolver is required");
        
        if(projectOperations == null){
            projectOperations = getProjectOperations();
        }
        Validate.notNull(projectOperations, "ProjectOperations is required");
        
        final LogicalPath resourcesPath = LogicalPath.getInstance(
                Path.SRC_MAIN_RESOURCES, moduleName);
        return isJpaInstallationPossible();
    }

    
    public String getName() {
        return FeatureNames.JPA;
    }

}
