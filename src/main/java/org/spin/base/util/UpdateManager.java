/*************************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                              *
 * This program is free software; you can redistribute it and/or modify it    		 *
 * under the terms version 2 or later of the GNU General Public License as published *
 * by the Free Software Foundation. This program is distributed in the hope   		 *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied 		 *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           		 *
 * See the GNU General Public License for more details.                       		 *
 * You should have received a copy of the GNU General Public License along    		 *
 * with this program; if not, write to the Free Software Foundation, Inc.,    		 *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     		 *
 * For the text or an alternative of this public license, you may reach us    		 *
 * Copyright (C) 2012-2018 E.R.P. Consultores y Asociados, S.A. All Rights Reserved. *
 * Contributor(s): Yamel Senih www.erpya.com				  		                 *
 *************************************************************************************/
package org.spin.base.util;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.spin.backend.grpc.update.ListPackagesRequest;
import org.spin.backend.grpc.update.ListPackagesResponse;
import org.spin.backend.grpc.update.ListStepsRequest;
import org.spin.backend.grpc.update.ListStepsResponse;
import org.spin.backend.grpc.update.ListUpdatesRequest;
import org.spin.backend.grpc.update.ListUpdatesResponse;
import org.spin.backend.grpc.update.UpdateCenterGrpc;
import org.spin.service.grpc.util.value.ValueManager;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * A helper class for update
 * @author Yamel Senih, ysenih@erpya.com , http://www.erpya.com
 */
public class UpdateManager {
	
	
	private static final Logger logger = Logger.getLogger(UpdateManager.class.getName());
	
	/**
	 * New instance for it
	 * @return
	 */
	public static UpdateManager newInstance() {
		return new UpdateManager();
	}
	
	/**	Port for connection	*/
	private int port;
	/**	Host	*/
	private String host;
	/**	Token	*/
	private String token;
	private ManagedChannel channel;
	private UpdateCenterGrpc.UpdateCenterBlockingStub blockingStub;
	/**
	 * @return the port
	 */
	public final int getPort() {
		return port;
	}
	/**
	 * @param port the port to set
	 */
	public final UpdateManager withPort(int port) {
		this.port = port;
		return this;
	}
	/**
	 * @return the host
	 */
	public final String getHost() {
		return host;
	}
	/**
	 * @param host the host to set
	 */
	public final UpdateManager withHost(String host) {
		this.host = host;
		return this;
	}
	/**
	 * @return the token
	 */
	public final String getToken() {
		return token;
	}
	/**
	 * @param token the token to set
	 */
	public final UpdateManager withToken(String token) {
		this.token = token;
		return this;
	}
	
	/**
	 * Prepare connection
	 */
	private void buildConnection() {
		channel = ManagedChannelBuilder.forAddress(host, port)
        // Channels are secure by default (via SSL/TLS). For the example we disable TLS to avoid
        // needing certificates.
        .usePlaintext()
        .build();
		blockingStub = UpdateCenterGrpc.newBlockingStub(channel);
	}
	
	/**
	 * close connection
	 */
	private void shutdown() {
		try {
			channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
		} catch (Exception e) {
			logger.severe(e.getLocalizedMessage());
		}
	}
	
	/**
	 * Get Available Packages
	 * @param version
	 * @param releaseNo
	 * @param pageToken
	 * @return
	 */
	public ListPackagesResponse listAvailablePackages(String version, String releaseNo, String pageToken) {
		try {
			buildConnection();
			return blockingStub.listPackages(
				ListPackagesRequest.newBuilder()
					.setPageToken(ValueManager.validateNull(pageToken))
					.setVersion(version)
					.setReleaseNo(releaseNo)
					.build()
			);
		} catch (Exception e) {
			throw e;
		} finally {
			shutdown();
		}
	}
	
	/**
	 * List Updates
	 * @param entityType
	 * @param releaseNo
	 * @param pageToken
	 * @return
	 */
	public ListUpdatesResponse listUpdates(String entityType, String releaseNo, String pageToken) {
		try {
			buildConnection();
			return blockingStub.listUpdates(
				ListUpdatesRequest.newBuilder()
					.setPageToken(ValueManager.validateNull(pageToken))
					.setEntityType(entityType)
					.setReleaseNo(ValueManager.validateNull(releaseNo))
					.build()
			);
		} catch (Exception e) {
			throw e;
		} finally {
			shutdown();
		}
	}
	
	/**
	 * List Steps from update
	 * @param updateId
	 * @param pageToken
	 * @return
	 */
	public ListStepsResponse listSteps(int updateId, String pageToken, int fromStep) {
		try {
			buildConnection();
			return blockingStub.listSteps(
				ListStepsRequest.newBuilder()
					.setPageToken(ValueManager.validateNull(pageToken))
					.setUpdateId(updateId)
					.setFromStep(fromStep)
					.build()
			);
		} catch (Exception e) {
			throw e;
		} finally {
			shutdown();
		}
	}
}
