package org.springframework.roo.addon.web.mvc.thymeleaf.addon;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.lang3.Validate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Service;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.web.mvc.controller.addon.responses.ControllerMVCResponseService;
import org.springframework.roo.classpath.PhysicalTypeCategory;
import org.springframework.roo.classpath.PhysicalTypeIdentifier;
import org.springframework.roo.classpath.TypeLocationService;
import org.springframework.roo.classpath.TypeManagementService;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetails;
import org.springframework.roo.classpath.details.ClassOrInterfaceTypeDetailsBuilder;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadata;
import org.springframework.roo.classpath.details.annotations.AnnotationMetadataBuilder;
import org.springframework.roo.model.JavaType;
import org.springframework.roo.model.RooJavaType;
import org.springframework.roo.project.Dependency;
import org.springframework.roo.project.FeatureNames;
import org.springframework.roo.project.LogicalPath;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PathResolver;
import org.springframework.roo.project.ProjectOperations;
import org.springframework.roo.project.maven.Pom;
import org.springframework.roo.support.logging.HandlerUtils;

/**
 * Implementation of ControllerMVCResponseService that provides
 * Thymeleaf Response types.
 * 
 * With this implementation, Spring Roo will be able to provide Thymeleaf response
 * types during controller generations.
 * 
 * @author Juan Carlos García
 * @since 2.0
 */
@Component
@Service
public class ThymeleafMVCViewResponseService implements ControllerMVCResponseService {

  private static Logger LOGGER = HandlerUtils.getLogger(ThymeleafMVCViewResponseService.class);
  private static final String RESPONSE_TYPE = "THYMELEAF";

  private final Dependency starterThymeleafDependency = new Dependency("org.springframework.boot",
      "spring-boot-starter-thymeleaf", null);
  private final Dependency layoutThymeleafDependency = new Dependency("nz.net.ultraq.thymeleaf",
      "thymeleaf-layout-dialect", null);

  // ------------ OSGi component attributes ----------------
  private BundleContext context;

  protected void activate(final ComponentContext context) {
    this.context = context.getBundleContext();
  }


  private ProjectOperations projectOperations;
  private TypeLocationService typeLocationService;
  private TypeManagementService typeManagementService;
  private PathResolver pathResolver;

  /**
   * This operation returns the Feature name. In this case,
   * the Feature name is the same as the response type.
   * 
   * @return String with TYHMELEAF as Feature name
   */
  @Override
  public String getName() {
    return getResponseType();
  }

  /**
   * This operation checks if this feature is installed in module.
   * THYMELEAF is installed in module if thymeleaf dependencies has been installed before.
   * 
   * @return true if thymeleaf dependencies has been installed, if not return false.
   */
  @Override
  public boolean isInstalledInModule(String moduleName) {
    // THYMELEAF is installed if Spring MVC has been installed and Thymeleaf
    // dependencies has been installed.
    return getProjectOperations().isFeatureInstalled(FeatureNames.MVC)
        && hasThymeleafDependencies(moduleName);
  }

  /**
   * This operation returns the THYMELEAF response type.
   * 
   * @return String with THYMELEAF as response type
   */
  @Override
  public String getResponseType() {
    return RESPONSE_TYPE;
  }

  /**
   * This operation returns the annotation type @RooThymeleaf
   * 
   * @return JavaType with the THYMELEAF annotation type
   */
  @Override
  public JavaType getAnnotation() {
    // Generating @RooThymeleaf annotation
    return RooJavaType.ROO_THYMELEAF;
  }

  /**
   * This operation annotates a controller with the THYMELEAF annotation
   * 
   * @param controller JavaType with the controller to be annotated.
   */
  @Override
  public void annotate(JavaType controller) {

    Validate.notNull(controller, "ERROR: You must provide a valid controller");

    ClassOrInterfaceTypeDetails controllerDetails =
        getTypeLocationService().getTypeDetails(controller);

    // Check if provided controller exists on current project
    Validate.notNull(controllerDetails, "ERROR: You must provide an existing controller");

    // Check if provided controller has been annotated with @RooController
    Validate.notNull(controllerDetails.getAnnotation(RooJavaType.ROO_CONTROLLER),
        "ERROR: You must provide a controller annotated with @RooController");

    // Add Thymeleaf annotation
    ClassOrInterfaceTypeDetailsBuilder typeBuilder =
        new ClassOrInterfaceTypeDetailsBuilder(controllerDetails);
    typeBuilder.addAnnotation(new AnnotationMetadataBuilder(getAnnotation()));

    // Write changes on provided controller
    getTypeManagementService().createOrUpdateTypeOnDisk(typeBuilder.build());

  }

  /**
   * This operation adds finders to the @RooThymeleaf annotation.
   * 
   * @param controller JavaType with the controller to be updated with
   *                   the finders.
   * @param finders List with finder names to be added.
   */
  @Override
  public void addFinders(JavaType controller, List<String> finders) {
    // TODO Auto-generated method stub

  }

  /**
   * This operation will check if some controller has the @RooThymeleaf annotation
   * 
   * @param controller JavaType with controller to check
   * @return true if provided controller has the THYMELEAF responseType.
   *        If not, return false.
   */
  @Override
  public boolean hasResponseType(JavaType controller) {
    Validate.notNull(controller, "ERROR: You must provide a valid controller");

    ClassOrInterfaceTypeDetails controllerDetails =
        getTypeLocationService().getTypeDetails(controller);

    if (controllerDetails == null) {
      return false;
    }

    return controllerDetails.getAnnotation(getAnnotation()) != null;
  }

  @Override
  public void install(Pom module) {
    // Is necessary to install thymeleaf dependencies
    addThymeleafDependencies(module);
    // Is necessary to generate the main controller
    addMainController(module);
    // Is necessary to copy static resources
    copyStaticResources(module);
    // Thymeleaf needs Datatables component to list
    // data, so is necessary to install Datatables resources
    addDatatablesResources(module);
  }

  /**
   * This operation returns the annotation type that identifies the main 
   * controller generated by THYMELEAF response type.
   * 
   * @return JavaType with the annotation managed by the implementation. 
   * This method never returns null.
   * 
   */
  @Override
  public JavaType getMainControllerAnnotation() {
    // Provides @RooThymeleafMainController
    return RooJavaType.ROO_THYMELEAF_MAIN_CONTROLLER;
  }

  /**
   * This operation returns the main controller generated by THYMELEAF response type.
   * 
   * 
   * @return JavaType with the main controller generated by the implementation.
   * Maybe returns null if main controller has not been generated yet.
   */
  @Override
  public JavaType getMainController() {
    Set<ClassOrInterfaceTypeDetails> mainController =
        getTypeLocationService().findClassesOrInterfaceDetailsWithAnnotation(
            getMainControllerAnnotation());
    if (mainController != null && !mainController.isEmpty()) {
      Iterator<ClassOrInterfaceTypeDetails> it = mainController.iterator();
      while (it.hasNext()) {
        return it.next().getType();
      }
    }
    return null;
  }

  /**
   * This method adds necessary thymeleaf dependencies to 
   * generated project
   * 
   * @param module
   */
  private void addThymeleafDependencies(Pom module) {

    // Add Thymeleaf starter
    getProjectOperations().addDependency(module.getModuleName(), starterThymeleafDependency);

    // Add Thymeleaf layout dialect
    getProjectOperations().addDependency(module.getModuleName(), layoutThymeleafDependency);

    // TODO: Add Thymeleaf tests dependencies 

  }

  /**
   * This method add new MainController.java class annotated
   * with @RooThymeleafMainController
   * 
   * This controller will be used to return index page.
   * 
   * @param module
   */
  private void addMainController(Pom module) {

    // Check if already exists other main controller annotated with @RooThymeleafMainController
    if (getMainController() != null) {
      return;
    }

    // If not, define new JavaType
    JavaType mainController =
        new JavaType(String.format("%s.web.MainController", getProjectOperations()
            .getTopLevelPackage(module.getModuleName())));

    // Check that new JavaType doesn't exists
    ClassOrInterfaceTypeDetails mainControllerDetails =
        getTypeLocationService().getTypeDetails(mainController);

    if (mainControllerDetails != null) {
      AnnotationMetadata mainControllerThymeleafAnnotation =
          mainControllerDetails.getAnnotation(getMainControllerAnnotation());
      // Maybe, this controller already exists
      if (mainControllerThymeleafAnnotation != null) {
        return;
      } else {
        throw new RuntimeException(
            "ERROR: You are trying to generate more than one MainController.");
      }
    }

    ClassOrInterfaceTypeDetailsBuilder cidBuilder = null;
    final LogicalPath controllerPath =
        getPathResolver().getPath(mainController.getModule(), Path.SRC_MAIN_JAVA);
    final String resourceIdentifier =
        getTypeLocationService().getPhysicalTypeCanonicalPath(mainController, controllerPath);
    final String declaredByMetadataId =
        PhysicalTypeIdentifier.createIdentifier(mainController,
            getPathResolver().getPath(resourceIdentifier));

    List<AnnotationMetadataBuilder> annotations = new ArrayList<AnnotationMetadataBuilder>();
    annotations.add(new AnnotationMetadataBuilder(getMainControllerAnnotation()));

    cidBuilder =
        new ClassOrInterfaceTypeDetailsBuilder(declaredByMetadataId, Modifier.PUBLIC,
            mainController, PhysicalTypeCategory.CLASS);
    cidBuilder.setAnnotations(annotations);

    getTypeManagementService().createOrUpdateTypeOnDisk(cidBuilder.build());
  }

  /**
   * This method copy and generate all necessary resources to be able
   * to use THYMELEAF.
   * 
   * @param module
   */
  private void copyStaticResources(Pom module) {
    // TODO
  }

  /**
   * This method install Datatables resources and Datatables support. This
   * is necessary because THYMELEAF response type uses Datatables components to
   * list results on generated views.
   * 
   * @param module
   */
  private void addDatatablesResources(Pom module) {
    // TODO Auto-generated method stub

  }

  /**
   * This method checks if THYMELEAF dependencies has been installed before
   * 
   * @param module
   * @return
   */
  private boolean hasThymeleafDependencies(String moduleName) {
    if (!getProjectOperations().getPomFromModuleName(moduleName)
        .getDependenciesExcludingVersion(starterThymeleafDependency).isEmpty()
        && !getProjectOperations().getPomFromModuleName(moduleName)
            .getDependenciesExcludingVersion(layoutThymeleafDependency).isEmpty()) {
      return true;
    }

    return false;
  }

  // Getting OSGi services

  public ProjectOperations getProjectOperations() {
    if (projectOperations == null) {
      // Get all Services implement ProjectOperations interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(ProjectOperations.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          projectOperations = (ProjectOperations) this.context.getService(ref);
          return projectOperations;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load ProjectOperations on ThymeleafMVCViewResponseService.");
        return null;
      }
    } else {
      return projectOperations;
    }
  }

  public TypeLocationService getTypeLocationService() {
    if (typeLocationService == null) {
      // Get all Services implement TypeLocationService interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(TypeLocationService.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          typeLocationService = (TypeLocationService) this.context.getService(ref);
          return typeLocationService;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load TypeLocationService on ThymeleafMVCViewResponseService.");
        return null;
      }
    } else {
      return typeLocationService;
    }
  }

  public TypeManagementService getTypeManagementService() {
    if (typeManagementService == null) {
      // Get all Services implement TypeManagementService interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(TypeManagementService.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          typeManagementService = (TypeManagementService) this.context.getService(ref);
          return typeManagementService;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load TypeManagementService on ThymeleafMVCViewResponseService.");
        return null;
      }
    } else {
      return typeManagementService;
    }
  }

  public PathResolver getPathResolver() {
    if (pathResolver == null) {
      // Get all Services implement PathResolver interface
      try {
        ServiceReference<?>[] references =
            this.context.getAllServiceReferences(PathResolver.class.getName(), null);

        for (ServiceReference<?> ref : references) {
          pathResolver = (PathResolver) this.context.getService(ref);
          return pathResolver;
        }

        return null;

      } catch (InvalidSyntaxException e) {
        LOGGER.warning("Cannot load PathResolver on ThymeleafMVCViewResponseService.");
        return null;
      }
    } else {
      return pathResolver;
    }
  }
}
