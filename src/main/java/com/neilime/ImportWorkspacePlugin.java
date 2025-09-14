package com.neilime;

import com.structurizr.Workspace;
import com.structurizr.dsl.*;
import com.structurizr.model.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ImportWorkspacePlugin implements StructurizrDslPlugin {

    @Override
    public void run(StructurizrDslPluginContext context) {
        String baseUrl = context.getParameter("BaseUrl");
        String includes = context.getParameter("Includes");

        for(String include : includes.split(",")) {
            includeWorkspace(context, baseUrl, include);
        }
    }

    private void includeWorkspace(StructurizrDslPluginContext context, String baseUrl, String include) {
        Workspace imported = null;

        imported = downloadWorkspace(baseUrl, include);

        if(imported == null) {
            return;
        }

        cloneElements(imported, context.getDslParser().getWorkspace().getModel());

        IdentifiersRegister register = context.getDslParser().getIdentifiersRegister();
        for(Element element: context.getDslParser().getWorkspace().getModel().getElements()){
            String canonicalName = element.getProperties().get("structurizr.dsl.identifier");
            if(register.getElement(canonicalName) != null) continue;
            String[] parts = canonicalName.split("\\.");
            register.register(parts[parts.length-1], element);
        }
    }

    private static Workspace downloadWorkspace(String baseUrl, String workspaceId) {
        Workspace imported;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/share/" + workspaceId + "/dsl"))
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String rawWorkspace = response.body();
            StructurizrDslParser parser = new StructurizrDslParser();
            parser.parse(rawWorkspace);
            imported = parser.getWorkspace();
        } catch (IOException | InterruptedException | StructurizrDslParserException e) {
            throw new RuntimeException(e);
        }

        return imported;
    }

    private void cloneElements(Workspace source, Model destination) {
        for(SoftwareSystem softwareSystem: source.getModel().getSoftwareSystems()){
            SoftwareSystem destinationSystem = destination.getSoftwareSystemWithName(softwareSystem.getName());
            if(destinationSystem == null) {continue;}
            cloneContainers(softwareSystem, destinationSystem);
        }

        for(Relationship relationship: source.getModel().getRelationships()){
            Element sourceParent = destination.getElementWithCanonicalName(relationship.getSource().getCanonicalName());
            Element destinationParent = destination.getElementWithCanonicalName(relationship.getDestination().getCanonicalName());
            if(sourceParent != null && destinationParent != null && !sourceParent.hasEfferentRelationshipWith(destinationParent, relationship.getDescription())) {
                addRelationShip(relationship, sourceParent, destinationParent);
            }
        }
    }

    private void addRelationShip(Relationship relationship, Element source, Element destination) {
        Relationship added = null;
        if(source instanceof SoftwareSystem sourceSystem &&  destination instanceof SoftwareSystem destSystem) {
            added = sourceSystem.uses(destSystem, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }
        if(source instanceof SoftwareSystem sourceSystem && destination instanceof Container destContainer) {
            added = sourceSystem.uses(destContainer, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }
        if(source instanceof SoftwareSystem sourceSystem && destination instanceof Component destComponent) {
            added = sourceSystem.uses(destComponent, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }

        if(source instanceof Container sourceContainer && destination instanceof SoftwareSystem destSystem) {
            added = sourceContainer.uses(destSystem, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }
        if(source instanceof Container sourceContainer && destination instanceof Container destContainer) {
            added = sourceContainer.uses(destContainer, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }
        if(source instanceof Container sourceContainer && destination instanceof Component destComponent) {
            added = sourceContainer.uses(destComponent, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }

        if(source instanceof Component sourceComponent && destination instanceof SoftwareSystem destSystem) {
            added = sourceComponent.uses(destSystem, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }
        if(source instanceof Component sourceComponent && destination instanceof Container destContainer) {
            added = sourceComponent.uses(destContainer, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }
        if(source instanceof Component sourceComponent && destination instanceof Component destComponent) {
            added = sourceComponent.uses(destComponent, relationship.getDescription(), relationship.getTechnology(), relationship.getInteractionStyle());
        }

        if(added == null) {return;}
        
        added.addTags(relationship.getTags());
        added.addProperties(relationship.getProperties());
    }  

    private void cloneContainers(SoftwareSystem source, SoftwareSystem destination) {
        for(Container container: source.getContainers()){
            Container added = destination.addContainer(container.getName(), container.getDescription(), container.getTechnology());
            added.addProperties(container.getProperties());
            added.addTags(container.getTags());
            cloneComponents(container, added);
        }
    }

    private void cloneComponents(Container source, Container destination) {
        for(Component component: source.getComponents()){
            Component added = destination.addComponent(component.getName(), component.getDescription(), component.getTechnology());
            added.addProperties(component.getProperties());
            added.addTags(component.getTags());
        }
    }
}
