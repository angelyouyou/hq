/*
 * NOTE: This copyright does *not* cover user programs that use HQ
 * program services by normal system calls through the application
 * program interfaces provided as part of the Hyperic Plug-in Development
 * Kit or the Hyperic Client Development Kit - this is merely considered
 * normal use of the program, and does *not* fall under the heading of
 * "derived work".
 * 
 * Copyright (C) [2004, 2005, 2006], Hyperic, Inc.
 * This file is part of HQ.
 * 
 * HQ is free software; you can redistribute it and/or modify
 * it under the terms version 2 of the GNU General Public License as
 * published by the Free Software Foundation. This program is distributed
 * in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
 * USA.
 */

package org.hyperic.hq.appdef.server.session;

import java.util.Iterator;
import java.util.List;

import javax.ejb.CreateException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperic.hq.appdef.shared.AIConversionUtil;
import org.hyperic.hq.appdef.shared.AIPlatformValue;
import org.hyperic.hq.appdef.shared.AIQApprovalException;
import org.hyperic.hq.appdef.shared.AIQueueConstants;
import org.hyperic.hq.appdef.shared.AIServerValue;
import org.hyperic.hq.appdef.shared.AppdefEntityID;
import org.hyperic.hq.appdef.shared.CPropManagerLocal;
import org.hyperic.hq.appdef.shared.ConfigManagerLocal;
import org.hyperic.hq.appdef.shared.PlatformManagerLocal;
import org.hyperic.hq.appdef.shared.ServerManagerLocal;
import org.hyperic.hq.appdef.shared.ServerValue;
import org.hyperic.hq.appdef.shared.ValidationException;
import org.hyperic.hq.authz.server.session.AuthzSubject;
import org.hyperic.hq.authz.shared.PermissionException;
import org.hyperic.hq.autoinventory.AIIp;
import org.hyperic.hq.autoinventory.AIPlatform;
import org.hyperic.hq.autoinventory.AIServer;
import org.hyperic.hq.common.ApplicationException;
import org.hyperic.hq.common.SystemException;
import org.hyperic.util.pager.PageControl;
import org.hyperic.util.pager.PageList;

/**
 * The AIQueueConstants.Q_DECISION_APPROVE means to add the queued
 * resource to the appdef model.  This visitor merges the queued resource
 * into appdef.
 */
public class AIQRV_approve implements AIQResourceVisitor {

    private static Log _log = LogFactory.getLog(AIQRV_approve.class);

    public AIQRV_approve () {}

    public void visitPlatform(AIPlatform aiplatform,
                              AuthzSubject subject,
                              PlatformManagerLocal pmLocal,
                              ConfigManagerLocal configMgr,
                              CPropManagerLocal cpropMgr,
                              List createdResources)
        throws AIQApprovalException, PermissionException {

        Integer id = aiplatform.getId();
        AppdefEntityID aid = null;

        _log.info("Visiting platform: " + id + " fqdn=" + aiplatform.getFqdn());
        AIPlatformValue aiplatformValue = aiplatform.getAIPlatformValue();
        Platform existingPlatform = getExistingPlatform(subject, pmLocal,
                                                        aiplatformValue);
        int qstat = aiplatform.getQueueStatus();
        switch (qstat) {
        case AIQueueConstants.Q_STATUS_PLACEHOLDER:
            // We don't approve placeholders.  Just let them sit
            // in the queue.
            break;

        case AIQueueConstants.Q_STATUS_ADDED:
            // This platform exists in the queue but not in appdef,
            // so add it to appdef.

            if (existingPlatform != null) {

                throw new AIQApprovalException("Platform already added at id " +
                                               existingPlatform.getId());
            }
            // Add the AI platform to appdef
            try {
                Platform platform =
                    pmLocal.createPlatform(subject,
                                           aiplatform.getAIPlatformValue());
                aid = platform.getEntityId();
                setCustomProperties(aiplatform, platform, cpropMgr);
                _log.info("Created platform " + platform.getName() + " id="
                          + platform.getId());
                createdResources.add(platform);
            } catch (PermissionException e) {
                throw e;
            }
            catch (ValidationException e) {
                throw new AIQApprovalException("Error creating platform from " +
                                               "AI data.", e);
            }
            catch (ApplicationException e) {
                throw new AIQApprovalException(e);
            }

            catch (CreateException e) {
                throw new AIQApprovalException(e);
            }
            
            try {
                configMgr.configureResource(subject, aid,
                                            aiplatform.getProductConfig(),
                                            aiplatform.getMeasurementConfig(),
                                            aiplatform.getControlConfig(),
                                            null, null, false, false);
            } catch (Exception e) {
                _log.warn("Error configuring platform: " + e, e);
            }

            break;

        case AIQueueConstants.Q_STATUS_CHANGED:
            // This platform exists in the queue and in appdef.
            // We wish to sync the appdef attributes to match
            // the queue.
            
            // Check to make sure the platform is still in appdef.

            // Update existing platform attributes.
            try {
                pmLocal.updateWithAI(aiplatformValue, subject.getName());
            } catch (Exception e) {
                throw new AIQApprovalException("Error updating platform using "
                                               + "AI data.", e);
            }

            setCustomProperties(aiplatform, existingPlatform, cpropMgr);

            if (aiplatformValue.isPlatformDevice()) {
                try {
                    configMgr.
                        configureResource(subject,
                                          existingPlatform.getEntityId(),
                                          aiplatform.getProductConfig(),
                                          aiplatform.getMeasurementConfig(),
                                          aiplatform.getControlConfig(),
                                          null, null, true, false);
                } catch (Exception e) {
                    _log.warn("Error configuring platform: " + e, e);
                }
            }
            _log.info("Appdef platform updated.");
            break;

        case AIQueueConstants.Q_STATUS_REMOVED:
            // This platform has been removed (in other words, AI no longer
            // detects it) however it is still present in the appdef model.
            // We wish to remove the appdef platform.

            // If the platform has already been removed, do nothing.
            if (existingPlatform == null) {
                _log.warn("Platform has already been removed, cannot " +
                          "remove aiplatform=" + id);
                return;
            }

            // Remove the platform
            try {
                pmLocal.removePlatform(subject, existingPlatform.getId());
            } catch (PermissionException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException("Error removing platform using " +
                                          "AI data.", e);
            }
            break;

        default:
            _log.error("Unknown queue state: " + qstat);
            throw new SystemException("Unknown queue state: " + qstat);
        }
    }

    public void visitIp(AIIp aiip, AuthzSubject subject,
                        PlatformManagerLocal platformMan)
        throws AIQApprovalException, PermissionException
    {
        Platform platform =
            getExistingPlatform(subject, platformMan,
                                aiip.getAIPlatform().getAIPlatformValue());
        int qstat = aiip.getQueueStatus();
        switch (qstat) {
        case AIQueueConstants.Q_STATUS_PLACEHOLDER:
            // Nothing to do
            break;
        case AIQueueConstants.Q_STATUS_ADDED:
            platformMan.addIp(platform, aiip.getAddress(), aiip.getNetmask(),
                              aiip.getMacAddress());
            break;
        case AIQueueConstants.Q_STATUS_CHANGED:
            platformMan.updateIp(platform, aiip.getAddress(), aiip.getNetmask(),
                              aiip.getMacAddress());
            break;
        case AIQueueConstants.Q_STATUS_REMOVED:
            platformMan.removeIp(platform, aiip.getAddress(),
                                 aiip.getNetmask(), aiip.getMacAddress());
            break;
        default:
            _log.error("Unknown queue state: " + qstat);
            throw new SystemException("Unknown queue state: " + qstat);
        }
    }

    public void visitServer(AIServer aiserver,
                            AuthzSubject subject,
                            PlatformManagerLocal pmLocal,
                            ServerManagerLocal smLocal,
                            ConfigManagerLocal configMgr,
                            CPropManagerLocal cpropMgr,
                            List createdResources)
        throws AIQApprovalException, PermissionException
    {
        Integer id = aiserver.getId();

        AIPlatform aiplatform;
        AIPlatformValue aiplatformValue;
        Platform existingPlatform;
        ServerValue serverValue = null;
        PageList serverValues;
        AIServerValue aiserverValue;
        boolean foundServer;
        Integer serverTypePK;

        // Get the aiplatform for this server
        aiplatform = aiserver.getAIPlatform();
        aiplatformValue = aiplatform.getAIPlatformValue();
        existingPlatform = getExistingPlatform(subject, pmLocal,
                                               aiplatformValue);

        int qstat = aiserver.getQueueStatus();
        switch (qstat) {
        case AIQueueConstants.Q_STATUS_PLACEHOLDER:
            // We don't approve placeholders.  Just let them sit
            // in the queue.
            break;

        case AIQueueConstants.Q_STATUS_ADDED:
            // This server exists in the queue but not in appdef,
            // so add it to appdef.

            // If the platform does not exist in appdef, throw an exception
            if (existingPlatform == null) {
                throw new AIQApprovalException("Platform does not exist for" +
                                               "AI platform id " +
                                               aiplatformValue.getId());
            }

            try {
                // Before we add it, make sure it's not already there...
                serverValues = smLocal.getServersByPlatform(subject,
                                                            existingPlatform.getId(),
                                                            false,
                                                            PageControl.PAGE_ALL);
                for (Iterator i = serverValues.iterator(); i.hasNext(); ) {
                    ServerValue server = (ServerValue)i.next();

                    if (server.getAutoinventoryIdentifier().
                        equals(aiserver.getAutoinventoryIdentifier())) {
                        // already added, throw exception
                        throw new AIQApprovalException("Server id " +
                                                       server.getId() +
                                                       " already added");
                    }
                }

                serverValue = AIConversionUtil.
                    convertAIServerToServer(aiserver.getAIServerValue(),
                                            smLocal);
                serverTypePK = serverValue.getServerType().getId();
                Server server = smLocal.createServer(subject,
                                                     existingPlatform.getId(),
                                                     serverTypePK,
                                                     serverValue);

                try {
                    configMgr.configureResource(subject,
                                                server.getEntityId(),
                                                aiserver.getProductConfig(),
                                                aiserver.getMeasurementConfig(),
                                                aiserver.getControlConfig(),
                                                null, /* RT config */
                                                null,
                                                false,
                                                false);
                } catch (Exception e) {
                    _log.warn("Error configuring server: " + e, e);
                }

                setCustomProperties(aiserver, server, cpropMgr);

                _log.info("Created server " + server.getName() + " id=" +
                          server.getId());

                createdResources.add(server);
            } catch (PermissionException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException("Error creating platform from " +
                                          "AI data: " + e.getMessage(), e);
            }

            break;

        case AIQueueConstants.Q_STATUS_CHANGED:
            // This server exists in the queue and in appdef.
            // We wish to sync the appdef attributes to match
            // the queue.

            // Check to make sure the platform and server are still in appdef.
            // If it has been removed, that's an error.
            if (existingPlatform == null) {
                throw new AIQApprovalException("Cannot find existing platform" +
                                               " for AI platform id " +
                                               aiplatform.getId());
            }

            try {
                // Find the server within the platform
                serverValues = smLocal.getServersByPlatform(subject,
                                                            existingPlatform.getId(),
                                                            false,
                                                            PageControl.PAGE_ALL);
                foundServer = false;
                for (Iterator i = serverValues.iterator(); i.hasNext(); ) {
                    ServerValue server = (ServerValue)i.next();

                    if (server.getAutoinventoryIdentifier()
                        .equals(aiserver.getAutoinventoryIdentifier())) {
                        aiserverValue = aiserver.getAIServerValue();
                        try {
                            serverValue
                                = smLocal.findServerValueById(subject,
                                                              server.getId());
                        } catch (Exception e) {
                            throw new SystemException("Error fetching server " +
                                                      "with id=" +
                                                      server.getId() +
                                                      ": " + e, e);
                        }
                        serverValue = AIConversionUtil.
                            mergeAIServerIntoServer(aiserverValue, serverValue);
                        foundServer = true;
                    }
                }
                if (!foundServer) {
                    throw new AIQApprovalException("Server id " + id +
                                                   " not found");
                }

                Server updated = smLocal.updateServer(subject, serverValue);
                try {
                    configMgr.configureResource(subject,
                                                serverValue.getEntityId(),
                                                aiserver.getProductConfig(),
                                                aiserver.getMeasurementConfig(),
                                                aiserver.getControlConfig(),
                                                null, null, true, false);
                } catch (Exception configE) {
                    _log.warn("Error configuring server: " + configE, configE);
                }

                setCustomProperties(aiserver, updated, cpropMgr);

                _log.info("Updated server " + updated.getName() + " id=" +
                          updated.getId());
            } catch (PermissionException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException("Error updating platform with " +
                                          "new AIServer data.", e);
            }

            break;

        case AIQueueConstants.Q_STATUS_REMOVED:
            // This server has been removed (in other words, AI no longer
            // detects it) however it is still present in the appdef model.
            // We wish to remove the appdef platform.

            // If the platform has already been removed, do nothing.
            if (existingPlatform == null) {
                _log.warn("Platform has already been removed, cannot " +
                          "remove aiserver=" + id);
                return;
            }

             try {
                 // Find the server within the platform
                 serverValues = smLocal.getServersByPlatform(subject,
                                                             existingPlatform.getId(),
                                                             false,
                                                             PageControl.PAGE_ALL);
                 foundServer = false;
                 for (Iterator i = serverValues.iterator(); i.hasNext(); ) {
                     ServerValue server = (ServerValue)i.next();
                     if (server.getAutoinventoryIdentifier().
                         equals(aiserver.getAutoinventoryIdentifier())) {
                         foundServer = true;
                         serverValue = server;
                     }
                 }
                 if (!foundServer) {
                     // Server has already been removed, return.
                     _log.warn("Server has already been removed, cannot " +
                               "remove aiserver=" + id);
                 }
                 _log.info("Removing server " + serverValue.getId());
                 smLocal.removeServer(subject, serverValue.getId());
            } catch (PermissionException e) {
                throw e;
            } catch (Exception e) {
                throw new SystemException("Error updating platform to remove" +
                                          " server, using AIServer data.", e);
            }
            break;

        default:
            _log.error("Unknown queue state: " + qstat);
            throw new SystemException("Unknown queue state: " + qstat);
        }
    }

    private Platform getExistingPlatform(AuthzSubject subject,
                                         PlatformManagerLocal pmLocal,
                                         AIPlatformValue aiplatform) {
        try {
            return pmLocal.getPlatformByAIPlatform(subject, aiplatform);
        } catch (PermissionException e) {
            throw new SystemException(e);
        }
    }

    private static void setCustomProperties(AIPlatform aiplatform,
                                            Platform platform,
                                            CPropManagerLocal cpropMgr) {
        try {
            int typeId =
                platform.getPlatformType().getId().intValue();
            cpropMgr.setConfigResponse(platform.getEntityId(),
                                       typeId,
                                       aiplatform.getCustomProperties());
        } catch (Exception e) {
            _log.warn("Error setting platform custom properties: " + e, e);
        }
    }

    private static void setCustomProperties(AIServer aiserver,
                                            Server server,
                                            CPropManagerLocal cpropMgr) {
        try {
            int typeId = server.getServerType().getId().intValue();
            cpropMgr.setConfigResponse(server.getEntityId(), typeId,
                                       aiserver.getCustomProperties());
        } catch (Exception e) {
            _log.warn("Error setting server custom properties: " + e, e);
        }
    }
}
