package net.fhirfactory.pegacorn.hestia.dam.im.processingplant.configuration;

import net.fhirfactory.pegacorn.core.model.topology.nodes.*;
import net.fhirfactory.pegacorn.deployment.properties.configurationfilebased.common.segments.ports.http.HTTPClientPortSegment;
import net.fhirfactory.pegacorn.deployment.properties.configurationfilebased.common.segments.ports.interact.InteractClientPortSegment;
import net.fhirfactory.pegacorn.deployment.topology.factories.archetypes.base.endpoints.HTTPTopologyEndpointFactory;
import net.fhirfactory.pegacorn.deployment.topology.factories.archetypes.fhirpersistence.im.FHIRIMSubsystemTopologyFactory;
import net.fhirfactory.pegacorn.core.model.topology.nodes.common.EndpointProviderInterface;
import net.fhirfactory.pegacorn.hestia.dam.im.common.HestiaIMNames;
import net.fhirfactory.pegacorn.util.PegacornEnvironmentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class HestiaMediaIMTopologyFactory extends FHIRIMSubsystemTopologyFactory {
    private static final Logger LOG = LoggerFactory.getLogger(HestiaMediaIMTopologyFactory.class);

    @Inject
    private HestiaIMNames hestiaIMNames;

    @Inject
    private PegacornEnvironmentProperties pegacornEnvironmentProperties;

    @Inject
    private HTTPTopologyEndpointFactory httpTopologyEndpointFactory;

    @Override
    protected Logger specifyLogger() {
        return (LOG);
    }

    @Override
    protected Class specifyPropertyFileClass() {
        return (HestiaMediaIMConfigurationFile.class);
    }

    @Override
    protected ProcessingPlantSoftwareComponent buildSubsystemTopology() {
        SubsystemTopologyNode subsystemTopologyNode = addSubsystemNode(getTopologyIM().getSolutionTopology());
        BusinessServiceTopologyNode businessServiceTopologyNode = addBusinessServiceNode(subsystemTopologyNode);
        DeploymentSiteTopologyNode deploymentSiteTopologyNode = addDeploymentSiteNode(businessServiceTopologyNode);
        ClusterServiceTopologyNode clusterServiceTopologyNode = addClusterServiceNode(deploymentSiteTopologyNode);

        PlatformTopologyNode platformTopologyNode = addPlatformNode(clusterServiceTopologyNode);
        ProcessingPlantSoftwareComponent processingPlantSoftwareComponent = addPegacornProcessingPlant(platformTopologyNode);
        addPrometheusPort(processingPlantSoftwareComponent);
        addJolokiaPort(processingPlantSoftwareComponent);
        addKubeLivelinessPort(processingPlantSoftwareComponent);
        addKubeReadinessPort(processingPlantSoftwareComponent);
        addEdgeAnswerPort(processingPlantSoftwareComponent);
        addAllJGroupsEndpoints(processingPlantSoftwareComponent);

        // Unique to HestiaIM
        getLogger().trace(".buildSubsystemTopology(): Add the httpFHIRClient port to the ProcessingPlant Topology Node");
        addHTTPClientPorts(processingPlantSoftwareComponent);
        return(processingPlantSoftwareComponent);
    }

    protected void addHTTPClientPorts( EndpointProviderInterface endpointProvider) {
        getLogger().debug(".addHTTPClientPorts(): Entry, endpointProvider->{}", endpointProvider);

        getLogger().trace(".addHTTPClientPorts(): Creating the HTTP Client (Used to Connect-To Hestia Media DM)");
        HTTPClientPortSegment interactHTTPClient = ((HestiaMediaIMConfigurationFile) getPropertyFile()).getInteractHestiaDMHTTPClient();
        httpTopologyEndpointFactory.newHTTPClientTopologyEndpoint(getPropertyFile(), endpointProvider, hestiaIMNames.getInteractHestiaDMHTTPClientName(),interactHTTPClient );

        getLogger().debug(".addHTTPClientPorts(): Exit");
    }

    protected String specifyPropertyFileName() {
        getLogger().info(".specifyPropertyFileName(): Entry");
        String configurationFileName = pegacornEnvironmentProperties.getMandatoryProperty("DEPLOYMENT_CONFIG_FILE");
        if(configurationFileName == null){
            throw(new RuntimeException("Cannot load configuration file!!!! (SUBSYSTEM-CONFIG_FILE="+configurationFileName+")"));
        }
        getLogger().info(".specifyPropertyFileName(): Exit, filename->{}", configurationFileName);
        return configurationFileName;
    }
}
