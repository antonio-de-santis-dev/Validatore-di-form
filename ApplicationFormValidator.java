package com.zerodd.sdipa.service;

import com.zerodd.sdipa.service.dto.JsonDTO.FormDTO;
import com.zerodd.sdipa.service.dto.JsonDTO.SectionDTO;
import com.zerodd.sdipa.service.dto.JsonDTO.FieldDTO;

import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ApplicationFormValidator {

    private final ObjectMapper objectMapper = new ObjectMapper();


    //Validazione della Struttura

    public List<String> validateStructure(String templateJson, String compiledJson){
        List<String> errors = new ArrayList<>();

        try {

            FormDTO template = objectMapper.readValue(templateJson, FormDTO.class);
            FormDTO compliled = objectMapper.readValue(compiledJson, FormDTO.class);

            if (template.getVersion() != compliled.getVersion()){
                errors.add("Different form versions: template version is " + template.getVersion() + ", compiled version is " + compliled.getVersion());
            }

            List<SectionDTO> tSections = template.getSections();
            List<SectionDTO> cSections = compliled.getSections();

            if(tSections.size() != cSections.size()){
                errors.add("Different number of sections: template has " + tSections.size() + ", compiled has " + cSections.size());
                return errors;
            }

            for (int i = 0; i < tSections.size(); i++){
                validateSection(tSections.get(i), cSections.get(i), errors);
            }

        } catch (Exception e){
            errors.add("Invalid or unreadable JSON: " + e.getMessage());
        }
        return errors;
    }



    private void validateSection (SectionDTO template, SectionDTO compiled, List<String>errors){

        if(!Objects.equals(template.getTitle(), compiled.getTitle())){
            errors.add("Different section titles: template title is " + template.getTitle() + ", compiled title is " + compiled.getTitle());
        }

        List<FieldDTO> tFields = template.getFields();
        List<FieldDTO> cFields = compiled.getFields();

        if (tFields.size() != cFields.size()){
            errors.add("Different number of fields in section " + template.getTitle() + ": template has " + tFields.size() + ", compiled has " + cFields.size());
            return;
        }

        for (int y = 0; y < tFields.size(); y++){
            validateField(tFields.get(y), cFields.get(y), template.getTitle(), errors);
        }
    }

    private void validateField(FieldDTO template, FieldDTO compiled, String sectionTitle, List<String> errors){

        if (!Objects.equals(template.getId(), compiled.getId())){
            errors.add("Different field IDs in section " + sectionTitle + ": template ID is " + template.getId() + ", compiled ID is " + compiled.getId());
        }

        if (!Objects.equals(template.getType(), compiled.getType())){
            errors.add("Different field types in section " + sectionTitle + ": template type is " + template.getType() + ", compiled type is " + compiled.getType());
        }

        if (!Objects.equals(template.getLabel(), compiled.getLabel())){
            errors.add("Different field labels in section " + sectionTitle + ": template label is " + template.getLabel() + ", compiled label is " + compiled.getLabel());
        }

        if (!Objects.equals(template.getOptions(), compiled.getOptions())){
            errors.add("Different field options in section " + sectionTitle + ": template options are " + template.getOptions() + ", compiled options are " + compiled.getOptions());
        }
    }

    //Validazione del contenuto

    public List<String> validateContent(String templateJson, String comiledJson){

        List<String> errors = new ArrayList<>();
        try {
            FormDTO template = objectMapper.readValue(templateJson, FormDTO.class);
            FormDTO compiled = objectMapper.readValue(comiledJson, FormDTO.class);

            List<SectionDTO> tSections = template.getSections();
            List<SectionDTO> cSections = compiled.getSections();

            int size = Math.min(tSections.size(), cSections.size());
            for (int z = 0; z < size; z++){
                validateSectionContent(tSections.get(z), cSections.get(z), errors);
            }
        }catch (Exception e){
            errors.add("JSON unreadable: " + e.getMessage());
        }
        return errors;
    }

    private void validateSectionContent(SectionDTO template, SectionDTO compiled, List<String> errors){

        int size = Math.min(template.getFields().size(), compiled.getFields().size());
        for (int x = 0; x < size; x++){
            validateFieldContent(template.getFields().get(x), compiled.getFields().get(x), template.getTitle(), errors);
        }
    }

    private void validateFieldContent(FieldDTO template, FieldDTO compiled, String sectionTitle, List<String> errors){

        String label = template.getLabel();
        String type = template.getType();
        Object value = compiled.getValue();

        boolean isArrayType = "multi".equals(type) || "check".equals(type);

        if (isArrayType) {

            List<String> valueList = castToStringList(value);

            if (template.isRequired() && valueList.isEmpty()){
                errors.add("Section\""+ sectionTitle+"\",field\""+label+"\": Required field not filled in." );
            }
            if (template.getOptions() != null){
                for (String item : valueList){
                    if (!template.getOptions().contains(item)){
                        errors.add("Section\""+ sectionTitle+"\",field\""+label+"\": Invalid value \""+item+"\". It is not among the permitted options.");
                    }
                }
            }
        } else {
            String valueStr = value != null ? value.toString().trim() : "";

            if (template.isRequired() && valueStr.isEmpty()){
                errors.add("Section\""+ sectionTitle+"\",field\""+label+"\": Required field not filled in.");
            }
            if ("select".equals(type) && !valueStr.isEmpty() && template.getOptions() != null && !template.getOptions().contains(valueStr)){
                errors.add("Section\""+ sectionTitle+"\",field\""+label+"\": Invalid value \""+valueStr+"\". It is not among the permitted options.");
            }
            if ("url".equals(type) && !valueStr.isEmpty() && !isValidUrl(valueStr)) {
                errors.add("Section\""+ sectionTitle+"\",field\""+label+"\": Invalid URL format.");
            }
        }
    }

    //mettodi supproto

    private List<String> castToStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
        }
        return List.of();
    }

    private boolean isValidUrl(String url) {
        try {
            new java.net.URI(url).toURL();
            return true;
        } catch (Exception e) {
            return false;
        }
    }


// metodo di comedo
    public List<String> validateAll(String templateJson, String compiledJson) {
        List<String> errors = new ArrayList<>(validateStructure(templateJson, compiledJson));
        if (errors.isEmpty()) {
            errors.addAll(validateContent(templateJson, compiledJson));
        }
        return errors;
    }
}
