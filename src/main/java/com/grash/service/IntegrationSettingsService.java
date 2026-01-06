package com.grash.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.IntegrationSettingsPatchDTO;
import com.grash.dto.LexwareCustomerDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Customer;
import com.grash.model.IntegrationSettings;
import com.grash.repository.IntegrationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class IntegrationSettingsService {
    private final IntegrationSettingsRepository integrationSettingsRepository;
    private final SecretEncryptionService secretEncryptionService;
    private final CustomerService customerService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${integrations.lexware.base-url:}")
    private String lexwareBaseUrl;

    @Value("${integrations.lexware.customers-path:/contacts}")
    private String lexwareCustomersPath;

    public Optional<IntegrationSettings> findByCompanySettings(Long companySettingsId) {
        return integrationSettingsRepository.findByCompanySettings_Id(companySettingsId);
    }

    public Optional<IntegrationSettings> findById(Long id) {
        return integrationSettingsRepository.findById(id);
    }

    public IntegrationSettings save(IntegrationSettings integrationSettings) {
        return integrationSettingsRepository.save(integrationSettings);
    }

    public IntegrationSettings updateLexwareSecret(Long id, IntegrationSettingsPatchDTO integrationSettingsPatchDTO) {
        Optional<IntegrationSettings> integrationSettingsOptional = integrationSettingsRepository.findById(id);
        if (integrationSettingsOptional.isEmpty()) {
            throw new CustomException("Integration settings not found", HttpStatus.NOT_FOUND);
        }
        IntegrationSettings integrationSettings = integrationSettingsOptional.get();
        String encryptedSecret = secretEncryptionService.encrypt(integrationSettingsPatchDTO.getLexwareSecret());
        integrationSettings.setLexwareSecretEncrypted(encryptedSecret);
        return integrationSettingsRepository.save(integrationSettings);
    }

    public List<Customer> syncLexwareCustomers(Company company) {
        Optional<IntegrationSettings> integrationSettingsOptional =
                integrationSettingsRepository.findByCompanySettings_Id(company.getCompanySettings().getId());
        if (integrationSettingsOptional.isEmpty()) {
            throw new CustomException("IntegrationSettings not found", HttpStatus.NOT_FOUND);
        }
        IntegrationSettings integrationSettings = integrationSettingsOptional.get();
        if (!StringUtils.hasText(integrationSettings.getLexwareSecretEncrypted())) {
            throw new CustomException("Lexware secret not configured", HttpStatus.BAD_REQUEST);
        }
        String secret = secretEncryptionService.decrypt(integrationSettings.getLexwareSecretEncrypted());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secret);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        if (!StringUtils.hasText(lexwareBaseUrl)) {
            throw new CustomException("Lexware base url missing", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String urlToCall = buildUrl(lexwareBaseUrl, lexwareCustomersPath);
        try {
            return fetchAllAndSaveCustomers(company, entity, urlToCall);
        } catch (HttpClientErrorException.NotFound e) {
            // try a common fallback path if only the base URL was provided
            String fallbackUrl = urlToCall.endsWith("/")
                    ? urlToCall + "contacts"
                    : urlToCall + "/contacts";
            try {
                return fetchAllAndSaveCustomers(company, entity, fallbackUrl);
            } catch (HttpClientErrorException.NotFound ex) {
                throw new CustomException("Lexware customers url not found: " + urlToCall + " or " + fallbackUrl, HttpStatus.BAD_GATEWAY);
            }
        } catch (RestClientException e) {
            throw new CustomException("Lexware sync failed: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private String buildUrl(String base, String path) {
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private List<Customer> fetchAllAndSaveCustomers(Company company, HttpEntity<Void> entity, String url) {
        int page = 0;
        int size = 50;
        List<Customer> saved = new ArrayList<>();
        while (true) {
            String pageUrl = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("role", "customer")
                    .queryParam("page", page)
                    .queryParam("size", size)
                    .toUriString();
            List<Customer> pageResult = fetchAndSavePage(company, entity, pageUrl);
            if (pageResult.isEmpty()) {
                break;
            }
            saved.addAll(pageResult);
            // heuristically stop if returned less than requested to avoid unnecessary extra call
            if (pageResult.size() < size) {
                break;
            }
            page++;
            // safeguard to prevent infinite loop
            if (page > 500) break;
        }
        return saved;
    }

    private List<Customer> fetchAndSavePage(Company company, HttpEntity<Void> entity, String url) {
        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, String.class);
        String body = response.getBody();
        if (body == null) return new ArrayList<>();
        List<LexwareCustomerDTO> customerDtos = parseCustomers(body);
        List<Customer> saved = new ArrayList<>();
        for (LexwareCustomerDTO dto : customerDtos) {
            if (!StringUtils.hasText(dto.getName())) {
                continue;
            }
            Customer customer = customerService
                    .findByNameIgnoreCaseAndCompany(dto.getName(), company.getId())
                    .orElseGet(Customer::new);
            customer.setCompany(company);
            customer.setName(dto.getName());
            customer.setAddress(dto.getAddress());
            customer.setPhone(dto.getPhone());
            customer.setWebsite(dto.getWebsite());
            customer.setEmail(dto.getEmail());
            customer.setCustomerType(dto.getCustomerType());
            customer.setDescription(dto.getDescription());
            customer.setCity(dto.getCity());
            customer.setZip(dto.getZip());
            customer.setCountryCode(dto.getCountryCode());
            customer.setCustomerNumber(dto.getCustomerNumber());
            customer.setVatNumber(dto.getVatNumber());
            customer.setBillingName(dto.getBillingName());
            customer.setBillingAddress(dto.getBillingAddress());
            customer.setBillingAddress2(dto.getBillingAddress2());
            saved.add(customerService.create(customer));
        }
        return saved;
    }

    private List<LexwareCustomerDTO> parseCustomers(String body) {
        List<LexwareCustomerDTO> result = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(body);
            List<JsonNode> candidates = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(candidates::add);
            } else {
                // try to find arrays under common keys
                String[] candidateKeys = new String[]{"content", "items", "data", "contacts", "results", "values"};
                for (String key : candidateKeys) {
                    JsonNode child = root.get(key);
                    if (child != null && child.isArray()) {
                        child.forEach(candidates::add);
                    }
                }
                if (candidates.isEmpty()) {
                    candidates.add(root);
                }
            }
            for (JsonNode node : candidates) {
                JsonNode roles = node.get("roles");
                boolean isCustomer = roles != null && roles.get("customer") != null;
                if (!isCustomer) continue;
                LexwareCustomerDTO dto = new LexwareCustomerDTO();
                JsonNode companyNode = node.get("company");
                dto.setName(text(companyNode, "name"));
                dto.setDescription(text(node, "note"));
                JsonNode addressNode = firstAddress(node, "billing");
                if (addressNode == null) {
                    addressNode = firstAddress(node, "primary");
                }
                if (addressNode != null) {
                    dto.setAddress(text(addressNode, "street"));
                    dto.setCity(text(addressNode, "city"));
                    dto.setZip(text(addressNode, "zip"));
                    dto.setCountryCode(text(addressNode, "countryCode"));
                }
                JsonNode phones = node.get("phoneNumbers");
                if (phones != null && phones.isArray() && phones.size() > 0) {
                    dto.setPhone(text(phones.get(0), "number"));
                }
                JsonNode emails = node.get("emailAddresses");
                if (emails != null && emails.isArray() && emails.size() > 0) {
                    dto.setEmail(text(emails.get(0), "email"));
                }
                JsonNode rolesCustomer = roles != null ? roles.get("customer") : null;
                if (rolesCustomer != null && rolesCustomer.get("number") != null) {
                    dto.setCustomerType(rolesCustomer.get("number").asText());
                    dto.setCustomerNumber(rolesCustomer.get("number").asText());
                }
                JsonNode vatNode = companyNode != null ? companyNode.get("vatRegistrationId") : null;
                if (vatNode != null) {
                    dto.setVatNumber(vatNode.asText());
                }
                if (StringUtils.hasText(dto.getName())) {
                    result.add(dto);
                }
            }
        } catch (IOException e) {
            throw new CustomException("Lexware sync failed: Unable to parse response. Raw: " + shorten(body), HttpStatus.BAD_GATEWAY);
        }
        return result;
    }

    private JsonNode firstAddress(JsonNode node, String key) {
        JsonNode addresses = node.get("addresses");
        if (addresses == null) return null;
        JsonNode list = addresses.get(key);
        if (list != null && list.isArray() && list.size() > 0) {
            return list.get(0);
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode val = node.get(field);
        return val != null && !val.isNull() ? val.asText() : null;
    }

    private String shorten(String input) {
        if (!StringUtils.hasText(input)) return "";
        int max = 500;
        return input.length() > max ? input.substring(0, max) + "..." : input;
    }
}
