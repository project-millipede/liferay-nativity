/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.nativity.control.win;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.liferay.nativity.Constants;
import com.liferay.nativity.control.NativityControl;
import com.liferay.nativity.control.NativityMessage;
import com.liferay.nativity.util.win.RegistryUtil;

import java.io.IOException;

import java.net.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gail Hernandez
 * @author Dennis Ju
 */
public class WindowsNativityControlImpl extends NativityControl {

	@Override
	public void addFavoritesPath(String path) {
		WindowsNativityUtil.addFavoritesPath(path);
	}

	@Override
	public boolean connect() {
		if (_connected) {
			return true;
		}

		boolean loaded = WindowsNativityUtil.load();

		if (!loaded) {
			_logger.debug("WindowsNativityUtil failed to load");

			return false;
		}

		if (!createSocket()) {
			return false;
		}

		Runnable runnable = new Runnable() {
			@Override
			public void run() {
				fireSocketOpenListeners();

				while (_connected) {
					handleConnection();
				}
			}
		};

		if(_executor.isShutdown()) {
			_executor = Executors.newCachedThreadPool();
		}
		_executor.execute(runnable);

		return true;
	}

	boolean createSocket() {
		int retries = 0;
		final Random random = new Random();
		final IOException socketThrowable = new IOException();
		try {
			if (_serverSocket == null || _serverSocket.isClosed()) {
				_serverSocket = new ServerSocket();
			}
			else if (_serverSocket.isBound()) {
				return true;
			}

			while (retries++ < 5) {
				_port = random.nextInt(16384) + 49152;
				try {
					final InetSocketAddress socketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), _port);
					_serverSocket.bind(socketAddress, 50);
					RegistryUtil.writeRegistry(Constants.NATIVITY_REGISTRY_KEY, Constants.PORT_REGISTRY_NAME, _port);
					return true;
				}
				catch (IOException ioe) {
					socketThrowable.addSuppressed(ioe);
				}
			}

			throw socketThrowable;
		}
		catch (IOException ex) {
			_logger.error(ex.getMessage(), ex);

			return false;
		}
	}

	@Override
	public boolean disconnect() {
		try {
			if (!_connected) {
				return true;
			}
			_serverSocket.close();
		}
		catch (IOException e) {
			_logger.error(e.getMessage(), e);
		}
		finally {
			_executor.shutdown();
		}
		_connected = false;

		return true;
	}

	@Override
	public Set<String> getAllObservedFolders() {
		return null;
	}

	@Override
	public boolean load() throws Exception {
		return true;
	}

	@Override
	public boolean loaded() {
		return true;
	}

	@Override
	public void refreshFiles(String[] paths) {
		if ((paths == null) || (paths.length == 0)) {
			return;
		}

		if (!WindowsNativityUtil.loaded()) {
			return;
		}

		try {
			for (String path : paths) {
				WindowsNativityUtil.updateExplorer(path);
			}
		}
		catch (UnsatisfiedLinkError ule) {
			_logger.error(ule.getMessage(), ule);
		}
	}

	@Override
	public void removeFavoritesPath(String path) {
		WindowsNativityUtil.removeFavoritesPath(path);
	}

	@Override
	public String sendMessage(NativityMessage nativityMessage) {
		return "";
	}

	@Override
	public void setFilterFolder(String folder) {
		setFilterFolders(new String[] { folder });
	}

	@Override
	public void setFilterFolders(String[] folders) {
		try {
			String foldersJson = _objectMapper.writeValueAsString(folders);

			RegistryUtil.writeRegistry(
				Constants.NATIVITY_REGISTRY_KEY,
				Constants.FILTER_FOLDERS_REGISTRY_NAME, foldersJson);
		}
		catch (JsonProcessingException jpe) {
			_logger.error(jpe.getMessage(), jpe);
		}

		for (String folder : folders) {
			WindowsNativityUtil.refreshExplorer(folder);
		}
	}

	@Override
	public void setPortFilePath(String path) {
	}

	@Override
	public void setSystemFolder(String folder) {
		if (!WindowsNativityUtil.loaded()) {
			return;
		}

		try {
			WindowsNativityUtil.setSystemFolder(folder);
		}
		catch (UnsatisfiedLinkError ule) {
			_logger.error(ule.getMessage(), ule);
		}
	}

	@Override
	public boolean unload() throws Exception {
		return true;
	}

	protected void handleConnection() {
		try {
			Socket clientSocket = _serverSocket.accept();

			_executor.execute(new MessageProcessor(clientSocket, this));
		}
		catch (SocketException se) {
			fireSocketCloseListeners();
		}
		catch (IOException e) {
			_logger.error(e.getMessage(), e);

			fireSocketCloseListeners();
		}
	}

	private static Logger _logger = LoggerFactory.getLogger(
		WindowsNativityControlImpl.class.getName());

	private static ObjectMapper _objectMapper = new ObjectMapper().configure(
		JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
	private static int _port = 33001;

	private boolean _connected = false;
	private ExecutorService _executor = Executors.newCachedThreadPool();
	private ServerSocket _serverSocket;

}