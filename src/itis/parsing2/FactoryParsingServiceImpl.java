package itis.parsing2;

import itis.parsing2.annotations.Concatenate;
import itis.parsing2.annotations.NotBlank;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class FactoryParsingServiceImpl implements FactoryParsingService {

    private final List<FactoryParsingException.FactoryValidationError> validationErrors = new ArrayList<>();

    @Override
    public Factory parseFactoryData(String factoryDataDirectoryPath) throws FactoryParsingException {
        Class<Factory> aClass = Factory.class;
        Field[] fields = aClass.getDeclaredFields();

        ArrayList<String> stringArrayList = new ArrayList<>();
        fillListWithFieldNames(stringArrayList, fields);

        Map<String, String> map = getMap(factoryDataDirectoryPath, stringArrayList);
        Factory factory = getResultFactory(aClass);

        fillFactoryFields(factory, map, fields);

        if (!validationErrors.isEmpty()) {
            throw new FactoryParsingException("Errors", validationErrors);
        } else {
            return factory;
        }
    }

    private Map<String, String> getMap(String path, ArrayList<String> fieldsNames){
        File file = new File(path);
        File[] files = file.listFiles();
        Map<String, String> dataMap = new HashMap<>();

        assert files != null;
        for (File f: files) {
            fillMap(dataMap, f, fieldsNames);
        }

        return dataMap;
    }

    private void fillMap(Map<String, String> map, File f, ArrayList<String> fieldsNames){
        try {
            BufferedReader br = new BufferedReader(new FileReader(f));

            br.readLine();
            String line = br.readLine();
            while (!line.equals("---")){
                String[] split = line.split(":");
                if(split.length == 2) {
                    split[0] = split[0].replace("\"", "").trim();
                    split[1] = split[1].replace("\"", "").trim();
                    if (fieldsNames.contains(split[0])) {
                        map.put(split[0], split[1]);
                    }
                }else{
                    if (fieldsNames.contains(split[0])) {
                        map.put(split[0], null);
                    }
                }
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fillListWithFieldNames(ArrayList<String> stringToBeFound, Field[] declaredFields) {
        for (Field f: declaredFields) {
            f.setAccessible(true);
            if(f.isAnnotationPresent(Concatenate.class)){
                stringToBeFound.addAll(Arrays.asList(f.getAnnotation(Concatenate.class).fieldNames()));
            }else{
                stringToBeFound.add(f.getName());
            }
        }
    }

    private void putIfConcatenatePresent(Field f, Factory factory, Map<String, String> map){
        Concatenate c = f.getAnnotation(Concatenate.class);
        String s = "";

        for (String fieldName: c.fieldNames()){
            if(map.containsKey(fieldName)){
                s += map.get(fieldName) + c.delimiter();
            }else{
                validationErrors.add(new FactoryParsingException.FactoryValidationError(f.getName(),
                        "There are no " + f.getName() +": "+ fieldName + " field in file"));
                return;
            }
        }

        s =
                s.substring(0, s.length() - c.delimiter().length());
        f.setAccessible(true);
        try {
            f.set(factory, s);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private List<String> getDepartmentsString(Map<String, String> map) {
        if(!map.containsKey("departments")){
            return null;
        }
        String[] str = map.get("departments")
                .replace("[","")
                .replace("]", "")
                .replace(",", "")
                .split(" ");
        return new ArrayList<>(Arrays.asList(str));
    }

    private void fillFactoryFields(Factory factory, Map<String, String> map, Field[] fields) {
        for (Field f : fields) {
            if (!f.isAnnotationPresent(Concatenate.class)) {
                if (f.isAnnotationPresent(NotBlank.class)) {
                    putIfNotBlankPresent(f, factory, map);
                } else {
                    if (f.getName().equals("amountOfWorkers")) {
                        f.setAccessible(true);
                        try {
                            if (map.get(f.getName()) != null) {
                                f.set(factory, Long.parseLong(map.get(f.getName())));
                            } else {
                                f.set(factory, null);
                            }
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    } else if (!f.getName().equals("description")) {
                        List<String> resList = getDepartmentsString(map);
                        f.setAccessible(true);
                        try {
                            f.set(factory, resList);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    } else {
                        f.setAccessible(true);
                        try {
                            f.set(factory, map.get(f.getName()));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                    }
                }
            } else {
                putIfConcatenatePresent(f, factory, map);
            }
        }
    }

    private Factory getResultFactory(Class<Factory> factoryClass) {
        try {
            Constructor<Factory> c = factoryClass.getConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void putIfNotBlankPresent(Field f, Factory factory, Map<String, String> map) {
        if (!map.containsKey(f.getName()) || map.get(f.getName()).equals("")
                || (map.get(f.getName()) == null)) {
            validationErrors.add(new FactoryParsingException.FactoryValidationError(f.getName(),
                    "Field " + f.getName() + " is empty in incoming file"));
        } else {
            f.setAccessible(true);
            try {
                f.set(factory, map.get(f.getName()));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
