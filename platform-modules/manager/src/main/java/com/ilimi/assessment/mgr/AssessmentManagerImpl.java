package com.ilimi.assessment.mgr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.ekstep.learning.common.enums.ContentAPIParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ilimi.assessment.dto.ItemSearchCriteria;
import com.ilimi.assessment.dto.ItemSetDTO;
import com.ilimi.assessment.dto.ItemSetSearchCriteria;
import com.ilimi.assessment.enums.AssessmentAPIParams;
import com.ilimi.assessment.enums.AssessmentErrorCodes;
import com.ilimi.assessment.util.AssessmentValidator;
import com.ilimi.common.dto.NodeDTO;
import com.ilimi.common.dto.Request;
import com.ilimi.common.dto.Response;
import com.ilimi.common.exception.ClientException;
import com.ilimi.common.exception.ResponseCode;
import com.ilimi.common.mgr.BaseManager;
import com.ilimi.common.util.ILogger;
import com.ilimi.common.util.PlatformLogger;
import com.ilimi.common.util.PlatformLogManager;
import com.ilimi.common.util.PlatformLogger;
import com.ilimi.graph.common.JSONUtils;
import com.ilimi.graph.dac.enums.GraphDACParams;
import com.ilimi.graph.dac.enums.RelationTypes;
import com.ilimi.graph.dac.enums.SystemNodeTypes;
import com.ilimi.graph.dac.model.Filter;
import com.ilimi.graph.dac.model.MetadataCriterion;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.graph.dac.model.Relation;
import com.ilimi.graph.dac.model.SearchConditions;
import com.ilimi.graph.dac.model.SearchCriteria;
import com.ilimi.graph.engine.router.GraphEngineManagers;
import com.ilimi.graph.exception.GraphEngineErrorCodes;
import com.ilimi.graph.model.node.DefinitionDTO;
import com.ilimi.graph.model.node.MetadataDefinition;
import com.ilimi.taxonomy.mgr.impl.TaxonomyManagerImpl;

@Component
public class AssessmentManagerImpl extends BaseManager implements IAssessmentManager {

	private static final String ITEM_SET_OBJECT_TYPE = "ItemSet";
	private static final String ITEM_SET_MEMBERS_TYPE = "AssessmentItem";

	private static ILogger LOGGER = PlatformLogManager.getLogger();

	private ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private AssessmentValidator validator;
	long[] sum = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };

	@SuppressWarnings("unchecked")
	@Override
	public Response createAssessmentItem(String taxonomyId, Request request) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		Node item = null;
		try {
			item = (Node) request.get(AssessmentAPIParams.assessment_item.name());
		} catch (Exception e) {
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_INVALID_REQUEST_FORMAT.name(),
					"Invalid request format");
		}
		if (null == item)
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM.name(),
					"AssessmentItem Object is blank");
		Boolean skipValidation = (Boolean) request.get(ContentAPIParams.skipValidations.name());
		if (null == skipValidation)
			skipValidation = false;
		Response validateRes = new Response();
		List<String> assessmentErrors = new ArrayList<String>();
		if (!skipValidation) {
			Request validateReq = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "validateNode");
			validateReq.put(GraphDACParams.node.name(), item);
			validateRes = getResponse(validateReq, LOGGER);
			assessmentErrors = validator.validateAssessmentItem(item);
		}
		if (checkError(validateRes) && !skipValidation) {
			if (assessmentErrors.size() > 0) {
				List<String> messages = (List<String>) validateRes.get(GraphDACParams.messages.name());
				messages.addAll(assessmentErrors);
			}
			return validateRes;
		} else {
			if (assessmentErrors.size() > 0 && !skipValidation) {
				return ERROR(GraphEngineErrorCodes.ERR_GRAPH_NODE_VALIDATION_FAILED.name(),
						"AssessmentItem validation failed", ResponseCode.CLIENT_ERROR, GraphDACParams.messages.name(),
						assessmentErrors);
			} else {
				replaceMediaItemsWithVariants(taxonomyId, item);
				Request createReq = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "createDataNode");
				createReq.put(GraphDACParams.node.name(), item);
				createReq.put(GraphDACParams.skip_validations.name(), skipValidation);
				Response createRes = getResponse(createReq, LOGGER);
				if (checkError(createRes)) {
					return createRes;
				} else {
					List<MetadataDefinition> newDefinitions = (List<MetadataDefinition>) request
							.get(AssessmentAPIParams.metadata_definitions.name());
					if (validateRequired(newDefinitions)) {
						Request defRequest = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER,
								"updateDefinition");
						defRequest.put(GraphDACParams.object_type.name(), item.getObjectType());
						defRequest.put(GraphDACParams.metadata_definitions.name(), newDefinitions);
						Response defResponse = getResponse(defRequest, LOGGER);
						if (checkError(defResponse)) {
							return defResponse;
						}
					}
				}
				return createRes;
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Response updateAssessmentItem(String id, String taxonomyId, Request request) {
		Node item = null;
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		if (StringUtils.isBlank(id))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM_ID.name(),
					"AssessmentItem Id is blank");
		try {
			item = (Node) request.get(AssessmentAPIParams.assessment_item.name());
		} catch (Exception e) {
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_INVALID_REQUEST_FORMAT.name(),
					"Invalid request format");
		}
		if (null == item)
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM.name(),
					"AssessmentItem Object is blank");
		Boolean skipValidation = (Boolean) request.get(ContentAPIParams.skipValidations.name());
		if (null == skipValidation)
			skipValidation = false;
		Response validateRes = new Response();
		List<String> assessmentErrors = new ArrayList<String>();
		if (!skipValidation) {
			Request validateReq = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "validateNode");
			validateReq.put(GraphDACParams.node.name(), item);
			validateRes = getResponse(validateReq, LOGGER);
			assessmentErrors = validator.validateAssessmentItem(item);
		}
		if (checkError(validateRes) && !skipValidation) {
			if (assessmentErrors.size() > 0) {
				List<String> messages = (List<String>) validateRes.get(GraphDACParams.messages.name());
				messages.addAll(assessmentErrors);
			}
			return validateRes;
		} else {
			if (assessmentErrors.size() > 0 && !skipValidation) {
				return ERROR(GraphEngineErrorCodes.ERR_GRAPH_NODE_VALIDATION_FAILED.name(), "Node validation failed",
						ResponseCode.CLIENT_ERROR, GraphDACParams.messages.name(), assessmentErrors);
			} else {
				if (null == item.getIdentifier())
					item.setIdentifier(id);
				replaceMediaItemsWithVariants(taxonomyId, item);
				Request updateReq = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "updateDataNode");
				updateReq.put(GraphDACParams.node.name(), item);
				updateReq.put(GraphDACParams.node_id.name(), item.getIdentifier());
				updateReq.put(GraphDACParams.skip_validations.name(), skipValidation);
				Response updateRes = getResponse(updateReq, LOGGER);
				if (checkError(updateRes)) {
					return updateRes;
				} else {
					List<MetadataDefinition> newDefinitions = (List<MetadataDefinition>) request
							.get(AssessmentAPIParams.metadata_definitions.name());
					if (validateRequired(newDefinitions)) {
						Request defRequest = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER,
								"updateDefinition");
						defRequest.put(GraphDACParams.object_type.name(), item.getObjectType());
						defRequest.put(GraphDACParams.metadata_definitions.name(), newDefinitions);
						Response defResponse = getResponse(defRequest, LOGGER);
						if (checkError(defResponse)) {
							return defResponse;
						}
					}
				}
				return updateRes;
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Response searchAssessmentItems(String taxonomyId, Request request) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		ItemSearchCriteria criteria = (ItemSearchCriteria) request
				.get(AssessmentAPIParams.assessment_search_criteria.name());

		if (null == criteria)
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_CRITERIA.name(),
					"AssessmentItem Search Criteria Object is blank");
		/*
		 * List<String> assessmentErrors =
		 * validator.validateAssessmentItemSet(request); if
		 * (assessmentErrors.size() > 0) throw new
		 * ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_CRITERIA.
		 * name(), "property can not be empty string");
		 */
		Request req = getRequest(taxonomyId, GraphEngineManagers.SEARCH_MANAGER, "searchNodes",
				GraphDACParams.search_criteria.name(), criteria.getSearchCriteria());
		Response response = getResponse(req, LOGGER);
		Response listRes = copyResponse(response);
		if (checkError(response)) {
			return response;
		} else {
			List<Node> nodes = (List<Node>) response.get(GraphDACParams.node_list.name());
			List<Map<String, Object>> searchItems = new ArrayList<Map<String, Object>>();
			if (null != nodes && nodes.size() > 0) {
				DefinitionDTO definition = getDefinition(taxonomyId, ITEM_SET_MEMBERS_TYPE);
				List<String> jsonProps = getJSONProperties(definition);
				for (Node node : nodes) {
					Map<String, Object> dto = getAssessmentItem(node, jsonProps, null);
					searchItems.add(dto);
				}
			}
			listRes.put(AssessmentAPIParams.assessment_items.name(), searchItems);
			return listRes;
		}
	}

	@Override
	public Response deleteAssessmentItem(String id, String taxonomyId) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		if (StringUtils.isBlank(id))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM_ID.name(),
					"AssessmentItem Id is blank");
		Request request = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "deleteDataNode",
				GraphDACParams.node_id.name(), id);
		return getResponse(request, LOGGER);
	}

	@Override
	public Response getAssessmentItem(String id, String taxonomyId, String[] ifields) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		if (StringUtils.isBlank(id))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM_ID.name(),
					"AssessmentItem Id is blank");
		Request request = getRequest(taxonomyId, GraphEngineManagers.SEARCH_MANAGER, "getDataNode",
				GraphDACParams.node_id.name(), id);
		request.put(GraphDACParams.get_tags.name(), true);
		Response getNodeRes = getResponse(request, LOGGER);
		Response response = copyResponse(getNodeRes);
		if (checkError(response)) {
			return response;
		}
		Node node = (Node) getNodeRes.get(GraphDACParams.node.name());
		if (null != node) {
			DefinitionDTO definition = getDefinition(taxonomyId, ITEM_SET_MEMBERS_TYPE);
			List<String> jsonProps = getJSONProperties(definition);
			Map<String, Object> dto = getAssessmentItem(node, jsonProps, ifields);
			response.put(AssessmentAPIParams.assessment_item.name(), dto);
		}
		return response;
	}

	private List<String> getJSONProperties(DefinitionDTO definition) {
		List<String> props = new ArrayList<String>();
		if (null != definition && null != definition.getProperties()) {
			for (MetadataDefinition mDef : definition.getProperties()) {
				if (StringUtils.equalsIgnoreCase("json", mDef.getDataType())) {
					props.add(mDef.getPropertyName());
				}
			}
		}
		return props;
	}

	private Map<String, Object> getAssessmentItem(Node node, List<String> jsonProps, String[] ifields) {
		Map<String, Object> metadata = new HashMap<String, Object>();
		metadata.put("subject", node.getGraphId());
		Map<String, Object> nodeMetadata = node.getMetadata();
		if (null != nodeMetadata && !nodeMetadata.isEmpty()) {
			if (null != ifields && ifields.length > 0) {
				List<String> fields = Arrays.asList(ifields);
				for (Entry<String, Object> entry : nodeMetadata.entrySet()) {
					if (null != entry.getValue()) {
						if (fields.contains(entry.getKey())) {
							if (jsonProps.contains(entry.getKey())) {
								Object val = JSONUtils.convertJSONString((String) entry.getValue());
								if (null != val)
									metadata.put(entry.getKey(), val);
							} else {
								metadata.put(entry.getKey(), entry.getValue());
							}
						}
					}
				}
			} else {
				for (Entry<String, Object> entry : nodeMetadata.entrySet()) {
					if (null != entry.getValue()) {
						if (jsonProps.contains(entry.getKey())) {
							Object val = JSONUtils.convertJSONString((String) entry.getValue());
							if (null != val)
								metadata.put(entry.getKey(), val);
						} else {
							metadata.put(entry.getKey(), entry.getValue());
						}
					}
				}
			}
		}
		if (null != node.getTags() && !node.getTags().isEmpty()) {
			metadata.put("tags", node.getTags());
		}
		if (null != node.getOutRelations() && !node.getOutRelations().isEmpty()) {
			List<NodeDTO> concepts = new ArrayList<NodeDTO>();
			for (Relation rel : node.getOutRelations()) {
				if (StringUtils.equals(RelationTypes.ASSOCIATED_TO.relationName(), rel.getRelationType())
						&& StringUtils.equalsIgnoreCase(SystemNodeTypes.DATA_NODE.name(), rel.getEndNodeType())) {
					if (StringUtils.equalsIgnoreCase("Concept", rel.getEndNodeObjectType())) {
						concepts.add(new NodeDTO(rel.getEndNodeId(), rel.getEndNodeName(), rel.getEndNodeObjectType(),
								rel.getRelationType()));
					}
				}
			}
			if (null != concepts && !concepts.isEmpty())
				metadata.put("concepts", concepts);
		}
		metadata.put("identifier", node.getIdentifier());
		return metadata;
	}

	@SuppressWarnings("unchecked")
	private List<String> getSetMembers(String taxonomyId, String setId) {
		Request setReq = getRequest(taxonomyId, GraphEngineManagers.COLLECTION_MANAGER, "getCollectionMembers");
		setReq.put(GraphDACParams.collection_type.name(), SystemNodeTypes.SET.name());
		setReq.put(GraphDACParams.collection_id.name(), setId);
		Response setRes = getResponse(setReq, LOGGER);
		List<String> members = (List<String>) setRes.get(GraphDACParams.members.name());
		return members;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Response createItemSet(String taxonomyId, Request request) {
		Node node = null;
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		try {
			   node = (Node) request.get(AssessmentAPIParams.assessment_item_set.name());
		} catch (Exception e) {
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_INVALID_REQUEST_FORMAT.name(),
					"Invalid request format");
		}
		if (null == node)
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM.name(),
					"AssessmentItemSet Object is blank");
		Boolean skipValidation = (Boolean) request.get(ContentAPIParams.skipValidations.name());
		if (null == skipValidation)
			skipValidation = false;
		Response validateRes = new Response();
		List<String> assessmentErrors = new ArrayList<String>();
		if (!skipValidation) {
			Request validateReq = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "validateNode");
			validateReq.put(GraphDACParams.node.name(), node);
			validateRes = getResponse(validateReq, LOGGER);
			assessmentErrors = validator.validateAssessmentItemSet(node);
		}
		if (checkError(validateRes) && !skipValidation) {
			if (assessmentErrors.size() > 0) {
				List<String> messages = (List<String>) validateRes.get(GraphDACParams.messages.name());
				messages.addAll(assessmentErrors);
			}
			return validateRes;
		} else {
			if (assessmentErrors.size() > 0 && !skipValidation) {
				return ERROR(GraphEngineErrorCodes.ERR_GRAPH_NODE_VALIDATION_FAILED.name(),
						"AssessmentItemSet validation failed", ResponseCode.CLIENT_ERROR,
						GraphDACParams.messages.name(), assessmentErrors);
			} else {
				Request setReq = getRequest(taxonomyId, GraphEngineManagers.COLLECTION_MANAGER, "createSet");
				setReq.put(GraphDACParams.criteria.name(), getItemSetCriteria(node));
				setReq.put(GraphDACParams.members.name(), getMemberIds(node));
				setReq.put(GraphDACParams.node.name(), node);
				setReq.put(GraphDACParams.object_type.name(), ITEM_SET_OBJECT_TYPE);
				setReq.put(GraphDACParams.member_type.name(), ITEM_SET_MEMBERS_TYPE);
				return getResponse(setReq, LOGGER);
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public Response updateItemSet(String id, String taxonomyId, Request request) {
		Node node = null;
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		if (StringUtils.isBlank(id))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM_SET_ID.name(),
					"AssessmentItemSet Id is blank");
		try{
			node = (Node) request.get(AssessmentAPIParams.assessment_item_set.name());
		} catch (Exception e) {
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_INVALID_REQUEST_FORMAT.name(),
					"Invalid request format");
		}
		if (null == node)
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM.name(),
					"AssessmentItemSet Object is blank");
		Request validateReq = getRequest(taxonomyId, GraphEngineManagers.NODE_MANAGER, "validateNode");
		validateReq.put(GraphDACParams.node.name(), node);
		Response validateRes = getResponse(validateReq, LOGGER);
		List<String> assessmentErrors = validator.validateAssessmentItemSet(node);
		if (checkError(validateRes)) {
			if (assessmentErrors.size() > 0) {
				List<String> messages = (List<String>) validateRes.get(GraphDACParams.messages.name());
				messages.addAll(assessmentErrors);
			}
			return validateRes;
		} else {
			if (assessmentErrors.size() > 0) {
				return ERROR(GraphEngineErrorCodes.ERR_GRAPH_NODE_VALIDATION_FAILED.name(),
						"AssessmentItemSet validation failed", ResponseCode.CLIENT_ERROR,
						GraphDACParams.messages.name(), assessmentErrors);
			} else {
				node.setIdentifier(id);
				Request setReq = getRequest(taxonomyId, GraphEngineManagers.COLLECTION_MANAGER, "updateSet");
				setReq.put(GraphDACParams.criteria.name(), getItemSetCriteria(node));
				setReq.put(GraphDACParams.members.name(), getMemberIds(node));
				setReq.put(GraphDACParams.node.name(), node);
				setReq.put(GraphDACParams.object_type.name(), ITEM_SET_OBJECT_TYPE);
				setReq.put(GraphDACParams.member_type.name(), ITEM_SET_MEMBERS_TYPE);
				return getResponse(setReq, LOGGER);
			}
		}
	}

	private String MEMBER_IDS_KEY = "memberIds";
	private String CRITERIA_KEY = "criteria";

	private SearchCriteria getItemSetCriteria(Node node) {
		if (null != node) {
			try {
				String strCriteria = (String) node.getMetadata().get(CRITERIA_KEY);
				if (StringUtils.isNotBlank(strCriteria)) {
					ItemSearchCriteria itemSearchCriteria = mapper.readValue(strCriteria, ItemSearchCriteria.class);
					return itemSearchCriteria.getSearchCriteria();
				}
			} catch (Exception e) {
				throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_INVALID_SEARCH_CRITERIA.name(),
						"Criteria given to create ItemSet is invalid.");
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private List<String> getMemberIds(Node node) {
		if (null != node && null != node.getMetadata()) {
			Object obj = node.getMetadata().get(MEMBER_IDS_KEY);
			if (null != obj) {
				node.getMetadata().remove(MEMBER_IDS_KEY);
				List<String> memberIds = mapper.convertValue(obj, List.class);
				return memberIds;
			}
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Response getItemSet(String id, String taxonomyId, String[] isfields, boolean expandItems) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		if (StringUtils.isBlank(id))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM_SET_ID.name(),
					"ItemSet Id is blank");
		Request request = getRequest(taxonomyId, GraphEngineManagers.COLLECTION_MANAGER, "getSet",
				GraphDACParams.collection_id.name(), id);
		Response getNodeRes = getResponse(request, LOGGER);
		Response response = copyResponse(getNodeRes);
		if (checkError(response)) {
			return response;
		}
		Node node = (Node) getNodeRes.get(GraphDACParams.node.name());
		if (null != node) {
			DefinitionDTO definition = getDefinition(taxonomyId, ITEM_SET_OBJECT_TYPE);
			List<String> jsonProps = getJSONProperties(definition);
			List<String> items = getSetMembers(taxonomyId, id);
			ItemSetDTO dto = new ItemSetDTO(node, items, isfields, jsonProps);
			Map<String, Object> itemSetMap = dto.returnMap();
			if (expandItems) {
				itemSetMap.remove("items");
				if (null != items && !items.isEmpty()) {
					Response searchRes = searchItems(taxonomyId, items);
					if (checkError(searchRes)) {
						return response;
					} else {
						DefinitionDTO itemDefinition = getDefinition(taxonomyId, ITEM_SET_MEMBERS_TYPE);
						List<String> itemJsonProps = getJSONProperties(itemDefinition);
						List<Object> list = (List<Object>) searchRes.get(AssessmentAPIParams.assessment_items.name());
						List<Map<String, Object>> itemMaps = new ArrayList<Map<String, Object>>();
						if (null != list && !list.isEmpty()) {
							for (Object obj : list) {
								List<Node> nodeList = (List<Node>) obj;
								for (Node itemNode : nodeList) {
									Map<String, Object> itemDto = getAssessmentItem(itemNode, itemJsonProps, null);
									itemMaps.add(itemDto);
								}
							}
						}
						Integer total = null;
						if (itemSetMap.get("total_items") instanceof Long)
							total = Integer.valueOf(((Long) itemSetMap.get("total_items")).intValue());
						else
							total = (Integer) itemSetMap.get("total_items");
						if (null == total) {
							total = itemMaps.size();
							itemSetMap.put("total_items", total);
						}
						Map<String, Object> itemSetCountMap = new HashMap<String, Object>();
						itemSetCountMap.put("id", node.getIdentifier());
						itemSetCountMap.put("count", total);
						List<Map<String, Object>> itemSetCountMaps = new ArrayList<Map<String, Object>>();
						itemSetCountMaps.add(itemSetCountMap);
						itemSetMap.put("item_sets", itemSetCountMaps);
						Map<String, Object> itemMap = new HashMap<String, Object>();
						itemMap.put(node.getIdentifier(), itemMaps);
						itemSetMap.put("items", itemMap);
					}
				}
			}
			response.put(AssessmentAPIParams.assessment_item_set.name(), itemSetMap);
		}
		return response;
	}

	private Response searchItems(String taxonomyId, List<String> itemIds) {
		SearchCriteria criteria = new SearchCriteria();
		List<Filter> filters = new ArrayList<Filter>();
		Filter filter = new Filter("identifier", SearchConditions.OP_IN, itemIds);
		filters.add(filter);
		MetadataCriterion metadata = MetadataCriterion.create(filters);
		criteria.addMetadata(metadata);
		List<Request> requests = new ArrayList<Request>();
		if (StringUtils.isNotBlank(taxonomyId)) {
			Request req = getRequest(taxonomyId, GraphEngineManagers.SEARCH_MANAGER, "searchNodes",
					GraphDACParams.search_criteria.name(), criteria);
			requests.add(req);
		} else {
			for (String tId : TaxonomyManagerImpl.taxonomyIds) {
				Request req = getRequest(tId, GraphEngineManagers.SEARCH_MANAGER, "searchNodes",
						GraphDACParams.search_criteria.name(), criteria);
				req.put(GraphDACParams.get_tags.name(), true);
				requests.add(req);
			}
		}
		Response response = getResponse(requests, LOGGER, GraphDACParams.node_list.name(),
				AssessmentAPIParams.assessment_items.name());
		return response;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Response searchItemSets(String taxonomyId, Request request) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		ItemSetSearchCriteria criteria = (ItemSetSearchCriteria) request
				.get(AssessmentAPIParams.assessment_search_criteria.name());

		if (null == criteria)
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_CRITERIA.name(),
					"ItemSet Search Criteria Object is blank");
		Request req = getRequest(taxonomyId, GraphEngineManagers.SEARCH_MANAGER, "searchNodes",
				GraphDACParams.search_criteria.name(), criteria.getSearchCriteria());
		Response response = getResponse(req, LOGGER);
		Response listRes = copyResponse(response);
		if (checkError(response)) {
			return response;
		} else {
			List<Node> nodes = (List<Node>) response.get(GraphDACParams.node_list.name());
			List<Map<String, Object>> searchItems = new ArrayList<Map<String, Object>>();
			if (null != nodes && nodes.size() > 0) {
				DefinitionDTO definition = getDefinition(taxonomyId, ITEM_SET_OBJECT_TYPE);
				List<String> jsonProps = getJSONProperties(definition);
				for (Node node : nodes) {
					List<String> items = getSetMembers(taxonomyId, node.getIdentifier());
					ItemSetDTO dto = new ItemSetDTO(node, items, null, jsonProps);
					searchItems.add(dto.returnMap());
				}
			}
			listRes.put(AssessmentAPIParams.assessment_item_sets.name(), searchItems);
			return listRes;
		}
	}

	@Override
	public Response deleteItemSet(String id, String taxonomyId) {
		if (StringUtils.isBlank(taxonomyId))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_TAXONOMY_ID.name(),
					"Taxonomy Id is blank");
		if (StringUtils.isBlank(id))
			throw new ClientException(AssessmentErrorCodes.ERR_ASSESSMENT_BLANK_ITEM_ID.name(),
					"AssessmentItem Id is blank");
		Request request = getRequest(taxonomyId, GraphEngineManagers.COLLECTION_MANAGER, "dropCollection",
				GraphDACParams.collection_id.name(), id);
		request.put(GraphDACParams.collection_type.name(), SystemNodeTypes.SET.name());
		return getResponse(request, LOGGER);
	}

	private DefinitionDTO getDefinition(String taxonomyId, String objectType) {
		Request request = getRequest(taxonomyId, GraphEngineManagers.SEARCH_MANAGER, "getNodeDefinition",
				GraphDACParams.object_type.name(), objectType);
		Response response = getResponse(request, LOGGER);
		if (!checkError(response)) {
			DefinitionDTO definition = (DefinitionDTO) response.get(GraphDACParams.definition_node.name());
			return definition;
		}
		return null;
	}

	private void replaceMediaItemsWithVariants(String graphId, Node item) {
		String media = (String) item.getMetadata().get(AssessmentAPIParams.media.name());
		boolean replaced = false;
		try {
			if (StringUtils.isNotBlank(media)) {
				TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<List<Map<String, Object>>>() {
				};
				List<Map<String, String>> mediaMap = mapper.readValue(media, typeRef);
				if (mediaMap != null && mediaMap.size() > 0) {
					DefinitionDTO definition = getDefinition(graphId, ITEM_SET_MEMBERS_TYPE);
					String resolution = "low";
					if (null != definition && null != definition.getMetadata() && !definition.getMetadata().isEmpty()) {
						Object defaultRes = definition.getMetadata().get("defaultRes");
						if (null != defaultRes && StringUtils.isNotBlank(defaultRes.toString()))
							resolution = defaultRes.toString();
					}
					for (Map<String, String> mediaItem : mediaMap) {
						String asset_id = (String) mediaItem.get(ContentAPIParams.asset_id.name());
						if (StringUtils.isBlank(asset_id))
							asset_id = (String) mediaItem.get(ContentAPIParams.assetId.name());
						if (StringUtils.isNotBlank(asset_id)) {
							Node asset = getNode(graphId, asset_id);
							if (asset != null) {
								String variantsJSON = (String) asset.getMetadata()
										.get(ContentAPIParams.variants.name());
								Map<String, String> variants = mapper.readValue(variantsJSON,
										new TypeReference<Map<String, String>>() {
										});
								if (variants != null && variants.size() > 0) {
									String lowVariantURL = variants.get(resolution);
									if (StringUtils.isNotEmpty(lowVariantURL)) {
										replaced = true;
										mediaItem.put(ContentAPIParams.src.name(), lowVariantURL);
									}
								}
							}
						}
					}
				}
				if (replaced) {
					String updatedMedia = mapper.writeValueAsString(mediaMap);
					item.getMetadata().put(AssessmentAPIParams.media.name(), updatedMedia);
				}
			}
		} catch (Exception e) {
			LOGGER.log(
					"error in replaceMediaItemsWithLowVariants while checking media for replacing with low variants, message= "
							, e.getMessage(), e);
			e.printStackTrace();
		}
	}

	/**
	 * Gets the node.
	 *
	 * @param taxonomyId
	 *            the taxonomy id
	 * @param contentId
	 *            the content id
	 * @return the node
	 */
	private Node getNode(String taxonomyId, String contentId) {
		Request request = getRequest(taxonomyId, GraphEngineManagers.SEARCH_MANAGER, "getDataNode",
				GraphDACParams.node_id.name(), contentId);
		request.put(GraphDACParams.get_tags.name(), true);
		Response getNodeRes = getResponse(request, LOGGER);
		Response response = copyResponse(getNodeRes);
		if (checkError(response)) {
			return null;
		}
		Node node = (Node) getNodeRes.get(GraphDACParams.node.name());
		return node;
	}
}
