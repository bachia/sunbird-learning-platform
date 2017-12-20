package com.ilimi.framework.mgr.impl;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ekstep.common.dto.Response;
import org.ekstep.common.exception.ClientException;
import org.ekstep.common.exception.ResponseCode;
import org.ekstep.common.exception.ServerException;
import org.ekstep.graph.dac.enums.GraphDACParams;
import org.ekstep.graph.dac.model.Node;
import org.ekstep.learning.common.enums.ContentErrorCodes;
import org.springframework.stereotype.Component;

import com.ilimi.framework.enums.CategoryEnum;
import com.ilimi.framework.mgr.ICategoryInstanceManager;

/**
 * This is the entry point for all CRUD operations related to category Instance
 * API.
 * 
 * @author Rashmi
 *
 */
@Component
public class CategoryInstanceManagerImpl extends BaseFrameworkManager implements ICategoryInstanceManager {

	private static final String CATEGORY_INSTANCE_OBJECT_TYPE = "CategoryInstance";

	private static final String GRAPH_ID = "domain";

	@Override
	public Response createCategoryInstance(String identifier, Map<String, Object> request) {
		if (null == request)
			return ERROR("ERR_INVALID_CATEGORY_INSTANCE_OBJECT", "Invalid Request", ResponseCode.CLIENT_ERROR);
		if (null == request.get("code") || StringUtils.isBlank((String) request.get("code")))
			return ERROR("ERR_CATEGORY_INSTANCE_CODE_REQUIRED", "Unique code is mandatory for categoryInstance",
					ResponseCode.CLIENT_ERROR);
		validateCategoryNode((String)request.get("code"));
		String id = generateIdentifier(identifier, (String) request.get("code"));
		if (null != id)
			request.put(CategoryEnum.identifier.name(), id);
		else
			throw new ServerException("ERR_SERVER_ERROR", "Unable to create CategoryInstanceId",
					ResponseCode.SERVER_ERROR);
		setRelations(identifier, request);
		return create(request, CATEGORY_INSTANCE_OBJECT_TYPE);
	}

	@Override
	public Response readCategoryInstance(String identifier, String categoryInstanceId) {
		categoryInstanceId = generateIdentifier(identifier, categoryInstanceId);
		if (validateScopeNode(categoryInstanceId, identifier)) {
			return read(categoryInstanceId, CATEGORY_INSTANCE_OBJECT_TYPE, CategoryEnum.categoryInstance.name());
		} else {
			throw new ClientException(
					ContentErrorCodes.ERR_CHANNEL_NOT_FOUND.name() + "/"
							+ ContentErrorCodes.ERR_FRAMEWORK_NOT_FOUND.name(),
					"Given channel/framework is not related to given category");
		}
	}

	@Override
	public Response updateCategoryInstance(String identifier, String categoryInstanceId, Map<String, Object> map) {
		if (null == map)
			return ERROR("ERR_INVALID_CATEGORY_INSTANCE_OBJECT", "Invalid Request", ResponseCode.CLIENT_ERROR);
		categoryInstanceId = generateIdentifier(identifier, categoryInstanceId);
		if (validateScopeNode(identifier, categoryInstanceId)) {
			return update(categoryInstanceId, CATEGORY_INSTANCE_OBJECT_TYPE, map);
		} else {
			throw new ClientException(
					ContentErrorCodes.ERR_CHANNEL_NOT_FOUND.name() + "/"
							+ ContentErrorCodes.ERR_FRAMEWORK_NOT_FOUND.name(),
					"Given channel/framework is not related to given category");
		}
	}

	@Override
	public Response searchCategoryInstance(String identifier, Map<String, Object> map) {
		if (null == map)
			return ERROR("ERR_INVALID_CATEGORY_INSTANCE_OBJECT", "Invalid Request", ResponseCode.CLIENT_ERROR);
		return search(map, CATEGORY_INSTANCE_OBJECT_TYPE, "categoryInstances", identifier);
	}

	@Override
	public Response retireCategoryInstance(String identifier, String categoryInstanceId) {
		categoryInstanceId = generateIdentifier(identifier, categoryInstanceId);
		if (validateScopeNode(identifier, categoryInstanceId)) {
			return retire(identifier, CATEGORY_INSTANCE_OBJECT_TYPE);
		} else {
			throw new ClientException(
					ContentErrorCodes.ERR_CHANNEL_NOT_FOUND.name() + "/"
							+ ContentErrorCodes.ERR_FRAMEWORK_NOT_FOUND.name(),
					"Given channel/framework is not related to given category");
		}

	}

	public boolean validateScopeId(String identifier) {
		if (StringUtils.isNotBlank(identifier)) {
			Response response = getDataNode(GRAPH_ID, identifier);
			if (checkError(response)) {
				return false;
			} else {
				Node node = (Node) response.get(GraphDACParams.node.name());
				if (StringUtils.equalsIgnoreCase(identifier, node.getIdentifier())) {
					return true;
				}
			}
		} else {
			throw new ClientException("ERR_INVALID_CHANNEL_ID/ERR_INVALID_FRAMEWORK_ID", "Required fields missing...");
		}
		return false;
	}
	
	private void validateCategoryNode(String code) {
		Response response = getDataNode(GRAPH_ID, code);
		if(checkError(response)) 
			throw new ClientException(ContentErrorCodes.ERR_CATEGORY_NOT_FOUND.name() + "/"
					+ ContentErrorCodes.ERR_CATEGORY_NOT_FOUND.name(),
			"Given category does not belong to master category data");
	}
}