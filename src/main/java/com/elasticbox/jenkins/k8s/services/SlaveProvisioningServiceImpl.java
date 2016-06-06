package com.elasticbox.jenkins.k8s.services;

import com.google.inject.Inject;

import com.elasticbox.jenkins.k8s.services.slavesprovisioning.chain.steps.SelectSuitablePodConfiguration;
import com.elasticbox.jenkins.k8s.services.slavesprovisioning.chain.PodDeploymentContext;
import com.elasticbox.jenkins.k8s.services.slavesprovisioning.chain.SlaveProvisioningStep;
import com.elasticbox.jenkins.k8s.plugin.clouds.PodSlaveConfigurationParams;
import com.elasticbox.jenkins.k8s.plugin.slaves.KubernetesSlave;
import com.elasticbox.jenkins.k8s.repositories.KubernetesRepository;
import com.elasticbox.jenkins.k8s.repositories.PodRepository;
import com.elasticbox.jenkins.k8s.plugin.clouds.KubernetesCloud;
import com.elasticbox.jenkins.k8s.services.error.ServiceException;

import hudson.Extension;
import hudson.model.Label;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class SlaveProvisioningServiceImpl implements SlaveProvisioningService {

    private static final Logger LOGGER = Logger.getLogger(SlaveProvisioningServiceImpl.class.getName());

    @Inject
    private PodRepository podRepository;

    @Inject
    private KubernetesRepository kubernetesRepository;

    @Inject
    private Set<SlaveProvisioningStep> podCreationChainHandlers;

    @Inject
    private SelectSuitablePodConfiguration selectSuitablePodConfiguration;


    public KubernetesSlave slaveProvision(KubernetesCloud kubernetesCloud,
                                          List<PodSlaveConfigurationParams> podConfigurations,
                                          Label label) throws ServiceException {

        PodDeploymentContext deploymentContext =
            new PodDeploymentContext.JenkinsPodSlaveDeploymentContextBuilder()
                .withJobLabel(label)
                                                .withOneOfThesePodConfigurations(podConfigurations)
                                                .intoKubernetesCloud(kubernetesCloud)
                                                .withNamespace(kubernetesCloud.getNamespace())
                                                .build();

        try {
            for (SlaveProvisioningStep deploymentHandler: podCreationChainHandlers) {
                deploymentHandler.handle(deploymentContext);
            }

            final KubernetesSlave kubernetesSlave = deploymentContext.getKubernetesSlave();

            LOGGER.log(Level.INFO, "The pod is running and the slave is online, provision done");

            return kubernetesSlave;

        } catch (ServiceException exception) {

            LOGGER.log(Level.SEVERE, "Error provisioning Pod Jenkins slave ", exception);

            throw exception;
        }


    }

    @Override
    public boolean canProvision(KubernetesCloud kubernetesCloud,
                                List<PodSlaveConfigurationParams> podConfigurations,
                                Label label) throws ServiceException {

        PodDeploymentContext deploymentContext =
            new PodDeploymentContext.JenkinsPodSlaveDeploymentContextBuilder()
                .withJobLabel(label)
                .withOneOfThesePodConfigurations(podConfigurations)
                .intoKubernetesCloud(kubernetesCloud)
                .withNamespace(kubernetesCloud.getNamespace())
                .build();


        this.selectSuitablePodConfiguration.handle(deploymentContext);

        return deploymentContext.getPodConfigurationChosen() != null;
    }


}