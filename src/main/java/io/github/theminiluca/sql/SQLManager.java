package io.github.theminiluca.sql;

import java.lang.reflect.*;
import java.sql.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

public final class SQLManager {

    public static boolean DEBUGGING = false;

    public static String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final Map<String, Connections> CONNECTIONS_CLASS = new HashMap<>();

    public static String standardateto(String format, long ms) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
        Date resultdate = new Date(ms);
        return simpleDateFormat.format(resultdate);
    }

    public static <T extends SQLObject> void sqlite(Class<? extends SQLObject> clazz,
                                                    Connections connections, HashMap<String, T> hash) {
        migration(clazz, connections);
        createTable(clazz, connections);
        try {
            SQLManager.load(clazz, hash);

        } catch (Exception e) {

        }
    }

    public static void migration(Class<? extends SQLObject> clazz, Connections connections) {
        if (migration(clazz) == null) return;
        try {
            String url = connections.connection.getMetaData().getURL().replace("jdbc:sqlite:", "");
            logging(new File(url), new File(url + "_migration"));
            new File(url).delete();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void logging(File sourceF, File targetF) {
        File[] ff = sourceF.listFiles();
        for (File file : ff) {
            File temp = new File(targetF.getAbsolutePath() + File.separator + file.getName());
            if (file.isDirectory()) {
                temp.mkdir();
                logging(file, temp);
            } else {
                FileInputStream fis = null;
                FileOutputStream fos = null;
                try {
                    fis = new FileInputStream(file);
                    fos = new FileOutputStream(temp);
                    byte[] b = new byte[4096];
                    int cnt = 0;
                    while ((cnt = fis.read(b)) != -1) {
                        fos.write(b, 0, cnt);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        fis.close();
                        fos.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }

                }
            }
        }
    }

    public static String tablename(String first, String last) {
        return first + "_" + last;
    }

    public static <T extends SQLObject> void load(Class<? extends SQLObject> clazz, HashMap<String, T> hash) {
        Connections connections = CONNECTIONS_CLASS.get(clazz.getName());
        load(clazz, connections.name, hash);
    }

    @SuppressWarnings("unchecked")
    public static <T extends SQLObject> void load(Class<? extends SQLObject> clazz, String table, HashMap<String, T> hash) {
        Connections connections = CONNECTIONS_CLASS.get(clazz.getName());
        LinkedHashMap<String, Object> instance = null;
        String sql = null;
        try {

            sql = "select * from " + table;
            Statement sta = connections.connection.createStatement();
            ResultSet res = sta.executeQuery(sql);
            while (res.next()) {
                instance = new LinkedHashMap<>();
                String primary = res.getString(primaryKey(clazz));
                for (String key : getcolumns(clazz).keySet()) {
                    instance.put(key, res.getObject(key));
                }
                for (Map.Entry<String, Field> entry : tableList(clazz).entrySet()) {
                    instance.put(entry.getKey(), loadList(connections, entry.getValue(), tablename(connections.name, entry.getKey()), clazz, primary));
                }
                for (Map.Entry<String, Field> entry : tableHash(clazz).entrySet()) {
                    instance.put(entry.getKey(), loadHash(connections, entry.getValue(), tablename(connections.name, entry.getKey()), clazz, primary));
                }
                hash.put(primary, (T) deserialize(clazz, instance));
            }
        } catch (Exception e) {

        } finally {
            if (DEBUGGING)
                System.out.println(sql);
        }
    }


    private static void loadingCore(Connections connections, String tableName, Class<? extends SQLObject> generic, LinkedHashMap<String, Object> instance, ResultSet res) throws SQLException {
        Class<? extends SQLObject> sqlClass = generic;
        String nextPrimary = res.getString(primaryKey(sqlClass));
        for (String key : getcolumns(sqlClass).keySet()) {
            instance.put(key, res.getObject(key));
        }
        for (Map.Entry<String, Field> entry : tableList(sqlClass).entrySet()) {
            instance.put(entry.getKey(), loadList(connections, entry.getValue(), tablename(tableName, entry.getKey()), sqlClass, nextPrimary));
        }
        for (Map.Entry<String, Field> entry : tableHash(sqlClass).entrySet()) {
            instance.put(entry.getKey(), loadHash(connections, entry.getValue(), tablename(tableName, entry.getKey()), sqlClass, nextPrimary));
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<Object, Object> loadHash(Connections connections, Field field, String tableName, Class<? extends SQLObject> superClass, String primary) {
        Map<Object, Object> hashmap = new HashMap<>();
        Class<?> generic_1 = getgeneric_1(field);
        Class<?> generic_2 = getgeneric_2(field);
        LinkedHashMap<String, Object> instance;
        String sql;
        sql = "select * from " + tableName + " where " + primaryKey(superClass) + "=" + wrapperMark(primary);
        try {
            Statement sta = connections.connection.createStatement();
            ResultSet res = sta.executeQuery(sql);
            boolean isObject = Arrays.stream(generic_1.getInterfaces()).filter(aClass -> aClass.equals(SQLObject.class)).findFirst().orElse(null) != null;
            while (res.next()) {
                instance = new LinkedHashMap<>();
                if (isObject) {
                    loadingCore(connections, tableName, (Class<? extends SQLObject>) generic_1, instance, res);
                    hashmap.put(res.getObject("keys"), deserialize((Class<? extends SQLObject>) generic_2, instance));
                } else {
                    String col = getclassname(generic_2);
                    hashmap.put(res.getObject("keys"), res.getObject(col));
                }
            }
        } catch (Exception e) {
            if (DEBUGGING) {
                System.err.println(field.getName());
            }
            e.printStackTrace();
        } finally {
            if (DEBUGGING) {
                System.out.println(sql);
            }

        }
        return hashmap;
    }

    public static void setConnection(Class<? extends SQLObject> clazz, Connections connections) {
        CONNECTIONS_CLASS.put(clazz.getName(), connections);
    }


    @SuppressWarnings("unchecked")
    public static void saveList(SQLObject object, String table) {
        Connections connections = getDriver(object);
        String sql = null;
        for (Map.Entry<String, Field> entry : tableList(object.getClass()).entrySet()) {
            try {
                String tableName = tablename((table == null ? connections.name : table), entry.getValue().getName());
                sql = "delete from " + tableName + " where " + primaryKey(object.getClass()) + "=" + wrapperMark(serialize(object).get(primaryKey(object.getClass())).toString());
                PreparedStatement statement = connections.connection.prepareStatement(sql);
                statement.execute();
                int n = 0;
                for (Object obj : (List<?>) entry.getValue().get(object)) {
                    String primary = value(serialize(object).get(primaryKey(object.getClass())));
                    if (obj instanceof SQLObject sqlObject) {
                        sql = "insert into " + tableName + "(" + primaryKey(object.getClass()) + ",indexs," + columns((Class<? extends SQLObject>) obj.getClass(), true, true) +
                                ") values (" + primary + "," + n + "," + values(sqlObject) + ")";

                        createLists(sqlObject.getClass(), tableName);
                        createHashes(sqlObject.getClass(), tableName);
                        saveList(sqlObject, tableName);
                        saveHash(sqlObject, tableName);
                    } else {
                        sql = "insert into " + tableName + "(" + primaryKey(object.getClass()) + ", indexs," + getclassname(obj.getClass()) +
                                ") values (" + value(serialize(object).get(primaryKey(object.getClass()))) + ", " + n + ", " + value(obj) + ")";
                    }
                    statement = connections.connection.prepareStatement(sql);
                    statement.execute();
                    n++;
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(entry.getKey() + "?????? ??????. ( " + sql + " )");
            } finally {
                if (DEBUGGING)
                    System.out.println("saveList function : " + sql);
            }
        }

    }

    public static String getclassname(Class<?> clazz) {
        String type = null;
        if (clazz.equals(long.class) || clazz.equals(Long.class)) {
            type = "long";
        } else if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
            type = "int";
        } else if (clazz.equals(UUID.class)) {
            type = "uuid";
        } else if (clazz.equals(String.class)) {
            type = "longtext";
        } else if (clazz.equals(boolean.class) || clazz.equals(Boolean.class)) {
            type = "boolean";
        } else return clazz.getSimpleName();
        return type;
    }

    private static String wrapperMark(String text) {
        return "\"" +
                text +
                "\"";
    }

    public static Class<?> getgeneric_1(Field field) {
        ParameterizedType generic = (ParameterizedType) field.getGenericType();
        return (Class<?>) generic.getActualTypeArguments()[0];
    }

    public static Class<?> getgeneric_2(Field field) {
        ParameterizedType generic = (ParameterizedType) field.getGenericType();
        return (Class<?>) generic.getActualTypeArguments()[1];
    }

    private static boolean exists(SQLObject object, Connections connections) {
        try {
            Statement statement = connections.connection.createStatement();
            ResultSet result = statement.executeQuery("select * from " + connections.name + " where " + primaryKey(object.getClass()) + "="
                    + wrapperMark(serialize(object).get(primaryKey(object.getClass())).toString()));
            return result.next();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public static Connections driver(String tableName, String path) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            return new Connections(tableName, connection);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void createList(Class<? extends SQLObject> clazz, String tableName, Class<?> generic) {
        Connections connections = CONNECTIONS_CLASS.getOrDefault(clazz.getName(), null);
        String sql = null;
        try {
            Statement statement = connections.connection.createStatement();
            String primary = getcolumn(getcolumns(clazz).get(primaryKey(clazz)));
            String column = getcolumn(generic);
            if (column == null) {
                sql = "create table if not exists " + tableName +
                        "(" + primaryKey(clazz) + " " + primary + ", indexs int, " + columns((Class<? extends SQLObject>) generic, false, true) + ")";
            } else {
                sql = "create table if not exists " + tableName +
                        "(" + primaryKey(clazz) + " " + primary + ", indexs int, " + column.toLowerCase() + " " + column + ")";
            }
            statement.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (DEBUGGING)
                System.out.println("createList function : " + sql);
        }
    }

    @SuppressWarnings("unchecked")
    private static void createHash(Class<? extends SQLObject> clazz, String tableName, Field generic) {
        Connections connections = CONNECTIONS_CLASS.getOrDefault(clazz.getName(), null);
        String sql = null;
        try {
            Statement statement = connections.connection.createStatement();
            String primary = getcolumn(getcolumns(clazz).get(primaryKey(clazz)));
            Class<?> generic_1 = getgeneric_1(generic);
            Class<?> generic_2 = getgeneric_2(generic);
            String column = getcolumn(generic_2);
            if (column == null) {
                sql = "create table if not exists " + tableName +
                        "(" + primaryKey(clazz) + " " + primary + ", keys " + getcolumn(generic_1) + ", " + columns((Class<? extends SQLObject>) generic_2, false, true) + ")";
            } else {
                sql = "create table if not exists " + tableName +
                        "(" + primaryKey(clazz) + " " + primary + ", keys " + getcolumn(generic_1) + ", " + column.toLowerCase() + " " + column + ")";
            }
            statement.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (DEBUGGING)
                System.out.println("createHash : " + sql);
        }
    }

    public static void save(SQLObject object) {
        save(object, getDriver(object));
    }

    public static void save(SQLObject object, Connections connections) {
        String sql;
        if (!exists(object, connections))
            sql = "INSERT INTO " + connections.name + "(" + columns(object.getClass(), true) + ") VALUES (" + values(object) + ")";
        else
            sql = "UPDATE " + connections.name + " SET " + setvalues(object) + " WHERE " + primaryKey(object.getClass()) + "=" + wrapperMark(serialize(object).get(primaryKey(object.getClass())).toString());
        try {
            PreparedStatement statement = connections.connection.prepareStatement(sql);
            statement.execute();
            statement.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (DEBUGGING)
                System.out.println("save function : " + sql);
        }
        saveList(object);
        saveHash(object);
    }

    public static void saveHash(SQLObject object) {
        saveHash(object, null);
    }

    @SuppressWarnings("unchecked")
    public static void saveHash(SQLObject object, String table) {
        Connections connections = getDriver(object);
        String sql = null;
        for (Map.Entry<String, Field> entry : tableHash(object.getClass()).entrySet()) {
            try {
                String tableName = tablename((table == null ? connections.name : table), entry.getValue().getName());
                sql = "delete from " + tableName + " where " + primaryKey(object.getClass()) + "=" + wrapperMark(serialize(object).get(primaryKey(object.getClass())).toString());
                PreparedStatement statement = connections.connection.prepareStatement(sql);
                statement.execute();
                Object ob = entry.getValue().get(object);
                for (Map.Entry<Object, Object> obj : ((Map<Object, Object>) ob).entrySet()) {
                    String primary = getcolumn(getcolumns(object.getClass()).get(primaryKey(object.getClass())));
                    String column = getcolumn(obj.getValue().getClass());
                    if (column == null) {
                        sql = "insert into " + tableName + "(" + primaryKey(object.getClass()) + ", keys," + columns((Class<? extends SQLObject>) obj.getValue().getClass(), false) +
                                ") values (" + primary + " " + value(obj.getKey()) + " " + values((SQLObject) obj.getValue());
                        createLists(object.getClass(), tableName);
                        createHashes(object.getClass(), tableName);
                        saveList(object, tableName);
                        saveHash(object, tableName);
                    } else {
                        sql = "insert into " + tableName + "(" + primaryKey(object.getClass()) + ", keys," + column +
                                ") values (" + value(serialize(object).get(primaryKey(object.getClass()))) + "," + value(obj.getKey()) + "," + value(obj.getValue()) + ")";
                    }
                    statement = connections.connection.prepareStatement(sql);
                    statement.execute();
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(entry.getKey() + "?????? ??????.");
            } finally {
                if (DEBUGGING)
                    System.out.println("saveHash function : " + sql);
            }
        }

    }

    public static void saveList(SQLObject object) {
        saveList(object, null);
    }

    public static Connections getDriver(SQLObject object) throws RuntimeException {
        return getDriver(object.getClass());
    }

    public static Connections getDriver(Class<? extends SQLObject> clazz) throws RuntimeException {
        Connections connections = CONNECTIONS_CLASS.getOrDefault(clazz.getName(), null);
        if (connections == null) {
            if (DEBUGGING)
                throw new RuntimeException("driver is not exists");
            else
                throw new RuntimeException("driver is not exists ( " + clazz + " )");
        }
        return connections;
    }

    public static void createLists(Class<? extends SQLObject> clazz, String table) {
        for (Field column : tableList(clazz).values()) {
            String columnName = column.getName();
            createList(clazz, tablename(table, columnName), getgeneric_1(column));
        }
    }

    public static void createHashes(Class<? extends SQLObject> clazz, String table) {
        for (Field column : tableHash(clazz).values()) {
            String columnName = column.getName();
            createHash(clazz, tablename(table, columnName), column);
        }
    }

    public static void createTable(Class<? extends SQLObject> clazz, Connections connection) {
        String sql = null;
        try {
            Statement statement = connection.connection.createStatement();
            sql = "create table if not exists " + connection.name + "(" + columns(clazz, false) + ")";
            setConnection(clazz, connection);
            statement.execute(sql);
            connection.connection.prepareStatement("pragma busy_timeout = 30000").execute();
            createLists(clazz, connection.name);
            createHashes(clazz, connection.name);

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (DEBUGGING) {
                System.out.println("createTable : " + sql);
            }
        }
    }


    private static String columns(Class<? extends SQLObject> clazz, boolean noType) {
        return columns(clazz, noType, false);
    }

    private static String columns(Class<? extends SQLObject> clazz, boolean noType, boolean noPrimary) {
        try {
            StringBuilder builder = new StringBuilder();
            LinkedHashMap<String, Class<?>> hash = getcolumns(clazz);
            int i = 0;
            for (Map.Entry<String, Class<?>> entry : hash.entrySet()) {
                String name = entry.getKey();
                Class<?> classes = entry.getValue();
                String type;
                if (!noType) {
                    type = getcolumn(classes);
                    builder.append("%s %s".formatted(name, type));
                    if (!noPrimary)
                        if (name.equals(primaryKey(clazz))) {
                            builder.append(" PRIMARY KEY");
                        }
                    builder.append(i == hash.size() - 1 ? "" : ",");
                } else {
                    builder.append("%s".formatted(name)).append(i == hash.size() - 1 ? "" : ",");
                }
                i++;
            }
            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String getcolumn(Class<?> classes) {
        String type;
        if (classes.equals(long.class) || classes.equals(Long.class)) {
            type = "LONG";
        } else if (classes.equals(int.class) || classes.equals(Integer.class)) {
            type = "INT";
        } else if (classes.equals(UUID.class)) {
            type = "VARCHAR(32)";
        } else if (classes.equals(String.class)) {
            type = "LONGTEXT";
        } else if (classes.equals(boolean.class) || classes.equals(Boolean.class)) {
            type = "BOOLEAN";
        } else return null;
        return type;
    }

    private static String setvalues(SQLObject object) {
        return values(object, true);
    }

    private static String values(SQLObject object) {
        return values(object, false);
    }

    private static String value(Object ob) {
        StringBuilder builder = new StringBuilder();
        if (ob == null) {
            builder.append("null");
        } else if (ob instanceof Integer) {
            builder.append((int) ob);
        } else if (ob instanceof UUID) {
            builder.append("\"%s\"".formatted(ob));
        } else if (ob instanceof String) {
            builder.append("\"%s\"".formatted(ob));
        } else if (ob instanceof Long) {
            builder.append((long) ob);
        } else if (ob instanceof Boolean) {
            builder.append((boolean) ob);
        }
        return builder.toString();
    }

    private static String values(SQLObject object, boolean update) {
        StringBuilder builder = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, Object> entry : serialize(object).entrySet()) {
            Object ob = entry.getValue();
            String name = entry.getKey();
            if (update) builder.append(name).append("=");
            builder.append(value(ob));
            builder.append(i == serialize(object).size() - 1 ? "" : ", ");
            i++;
        }
        return builder.toString();
    }


    private static <T extends SQLObject> LinkedHashMap<String, Field> tableList(Class<T> clazz) {
        List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field
                -> field.isAnnotationPresent(SQL.class) && field.getType().equals(List.class)).toList();
        LinkedHashMap<String, Field> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            field.setAccessible(true);
            hash.put(field.getName(), field);
        }
        return hash;
    }

    private static LinkedHashMap<String, Field> tableColumns(Class<? extends SQLObject> clazz) {
        List<Field> fields = fields(clazz);
        LinkedHashMap<String, Field> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            field.setAccessible(true);
            hash.put(field.getName(), field);
        }
        return hash;
    }

    private static LinkedHashMap<String, Field> tableHash(Class<? extends SQLObject> clazz) {
        List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field
                -> field.isAnnotationPresent(SQL.class) && (field.getType().equals(Map.class) || field.getType().equals(HashMap.class))).toList();
        LinkedHashMap<String, Field> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            field.setAccessible(true);
            hash.put(field.getName(), field);
        }
        return hash;
    }

    private static LinkedHashMap<String, Class<?>> classArgument(Class<? extends SQLObject> clazz) {
        List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field
                -> field.isAnnotationPresent(SQL.class) && !field.getType().equals(List.class)).toList();
        LinkedHashMap<String, Class<?>> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            hash.put(field.getName(), field.getType());
        }
        return hash;
    }


    private static LinkedHashMap<String, Class<?>> getcolumns(Class<? extends SQLObject> clazz) {
        List<Field> fields = fields(clazz);
        LinkedHashMap<String, Class<?>> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            hash.put(field.getName(), field.getType());
        }
        return hash;
    }

    private static LinkedHashMap<String, Class<?>> getlists(Class<? extends SQLObject> clazz) {
        List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field
                -> field.isAnnotationPresent(SQL.class) && field.getType().equals(List.class)).toList();
        LinkedHashMap<String, Class<?>> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            hash.put(field.getName(), field.getType());
        }
        return hash;
    }

    private static LinkedHashMap<String, Class<?>> gethashes(Class<? extends SQLObject> clazz) {
        List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field
                -> field.isAnnotationPresent(SQL.class) && field.getType().equals(Map.class)).toList();
        LinkedHashMap<String, Class<?>> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            hash.put(field.getName(), field.getType());
        }
        return hash;
    }

    private static List<Field> fields(Class<? extends SQLObject> clazz) {
        return Arrays.stream(clazz.getDeclaredFields()).filter(field ->
                field.isAnnotationPresent(SQL.class) && !field.getType().equals(List.class) && !field.getType().equals(Map.class) && !field.getType().equals(HashMap.class)).toList();
    }


    private static <T extends SQLObject> LinkedHashMap<String, Object> serialize(T object) {
        List<Field> fields = fields(object.getClass());
        LinkedHashMap<String, Object> hash = new LinkedHashMap<>();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                hash.put(field.getName(), field.get(object));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("%s".formatted(field.getName()));
            }
        }
        return hash;
    }

    private static String primaryKey(Class<? extends SQLObject> clazz) {
        try {
            List<Field> fields = Objects.requireNonNull(Arrays.stream(clazz.getDeclaredFields()).filter(field ->
                    field.isAnnotationPresent(SQL.class) && field.getAnnotation(SQL.class).primary()).toList());
            if (fields.size() > 1) {
                System.err.println("%s ??? primary ??? ????????? 2??? ??????????????????.".formatted(clazz.getName()));
            }
            return fields.get(0).getName();
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    @SuppressWarnings("unchecked")
    public static HashMap<String, Object> migration(Class<? extends SQLObject> clazz) {
        try {
            Field migration = clazz.getDeclaredField("migration");
            if (migration.get(null) != null) return (HashMap<String, Object>) migration.get(null);
            else return null;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return null;
        }
    }

    public static boolean isMigration(Class<? extends SQLObject> clazz) {
        return getcolumns(clazz).keySet().equals(getcolumns_db(clazz));
    }

    public static Set<String> getcolumns_db(Class<? extends SQLObject> clazz) {
        try {
            Connections connections = getDriver(clazz);
            Statement sta = connections.connection.createStatement();
            ResultSet res = sta.executeQuery("PRAGMA table_info(" + connections.name + ")");
            Set<String> columns = new HashSet<>();
            while (res.next()) {
                columns.add(res.getString("name"));
            }
            return columns;
        } catch (Exception e) {
            return null;
        }
    }

    public static Set<String> getables_db(Class<?> clazz, Connection connection) {
        try {
            Statement sta = connection.createStatement();
            ResultSet res = sta.executeQuery("select tbl_name as columns from sqlite_master where type='table'");
            Set<String> columns = new HashSet<>();
            while (res.next()) {
                columns.add(res.getString("columns"));
            }
            return columns;
        } catch (Exception e) {
            return null;
        }
    }


//    public static void migration(Class<? extends SQLObject> clazz, Connection connection, String table) {
//        if (isMigration(clazz)) {
//            Statement sta = null;
//            try {
//                sta = connection.createStatement();
//                if (migration(clazz) == null) {
//                    throw new RuntimeException("????????????????????? ??????????????? migration ???????????? ???????????? ???????????? null ????????????.");
//                } else {
//                    HashMap<String, Object> migration = migration(clazz);
//                    Set<String> columns_db = getcolumns_db(clazz);
//                    LinkedHashMap<String, Class<?>> getcolumns = getcolumns(clazz);
//                    Set<String> columns = getcolumns.keySet();
//
//                    Set<String> alter_columns = new HashSet<>(columns);
//                    alter_columns.remove(columns_db);
//                    if (alter_columns.size() != 0) {
//                        for (String name : alter_columns) {
//                            Class<?> classes = getcolumns.get(name);
//                            String type = getcolumn(classes);
//                            String sql = "alter table " + table + " add " + name + " " + type;
//                            sta.execute(sql);
//                        }
//                    }
//                    alter_columns = new HashSet<>(columns_db);
//                    alter_columns.removeAll(columns);
//                    if (alter_columns.size() != 0) {
//                        for (String name : columns) {
//                            String sql = "alter table " + table + " drop column " + name;
//                            sta.execute(sql);
//                        }
//                    }
//                }
//                for (Map.Entry<String, Field> entry : tableList(clazz).entrySet()) {
//                    migrationList(connection, entry.getValue(), tablename(table, entry.getKey()), clazz);
//                }
//            } catch (SQLException e) {
//                throw new RuntimeException(e);
//            }
//        }
//    }
//
//    @SuppressWarnings("unchecked")
//    private static void migrationList(Connection connection, Field field, String table, Class<? extends SQLObject> superClass) {
//        try {
//            Statement sta = connection.createStatement();
//            Class<?> clazz = getgeneric_1(field);
//            Set<String> table_db = getables_db(clazz, connection);
//            LinkedHashMap<String, Class<?>> getlists = getlists(superClass);
//            Set<String> lists = getlists.keySet();
//
//            Set<String> alter_tables = new HashSet<>(lists);
//            alter_tables.removeAll(table_db);
//            if (alter_tables.size() != 0) {
//                for (String name : alter_tables) {
//                    String sql = "drop table " + name;
//                    sta.execute(sql);
//                }
//            }
//
//            alter_tables = new HashSet<>(table_db);
//            alter_tables.removeAll(lists);
//            if (alter_tables.size() != 0) {
//                for (String name : alter_tables) {
//                    Class<? extends SQLObject> sqlObject = (Class<? extends SQLObject>) clazz;
//                    createList(sqlObject, tablename(table, name), 13);
//                }
//            }
//
////            if (hasSQLObject(clazz)) {
////                Class<? extends SQLObject> sqlObject = (Class<? extends SQLObject>) clazz;
////                migration(sqlObject, connection, table);
////            } else {
////
////            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

    @SuppressWarnings("unchecked")
    public static List<Object> loadList(Connections connections, Field field, String tableName, Class<? extends SQLObject> superClass, String primary) {
        final List<Object> list = new ArrayList<>();
        Class<?> generic = getgeneric_1(field);
        LinkedHashMap<String, Object> instance;
        String sql;
        sql = "select * from " + tableName + " where " + primaryKey(superClass) + "=" + wrapperMark(primary) + " order by indexs asc";
        try {
            Statement sta = connections.connection.createStatement();
            ResultSet res = sta.executeQuery(sql);
            while (res.next()) {
                instance = new LinkedHashMap<>();
                if (hasSQLObject(generic)) {
                    loadingCore(connections, tableName, (Class<? extends SQLObject>) generic, instance, res);
                    list.add(deserialize((Class<? extends SQLObject>) generic, instance));
                } else {
                    String col = getclassname(generic);
                    list.add(res.getObject(col));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static boolean hasSQLObject(Class<?> generic) {
        return Arrays.stream(generic.getInterfaces()).filter(aClass -> aClass.equals(SQLObject.class)).findFirst().orElse(null) != null;
    }

    public static SQLObject deserialize(Class<? extends SQLObject> clazz, LinkedHashMap<String, Object> hash) {
        Object[] objects = null;
        Class<?>[] classes = null;

        try {
            List<Field> fields = Arrays.stream(clazz.getDeclaredFields()).filter(field ->
                    field.isAnnotationPresent(SQL.class)).toList();
            objects = new Object[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                String name = fields.get(i).getName();
                objects[i] = hash.getOrDefault(name, null);
            }
            classes = new Class[fields.size()];
            for (int i = 0; i < classes.length; i++) {
                classes[i] = objects[i].getClass();
            }
            Constructor<? extends SQLObject> constructor;
            constructor = clazz.getConstructor(classes);
            if (!constructor.isAnnotationPresent(SQLConstructor.class)) {
                System.err.println("SQLConstructor ??? ???????????? ???????????????.");
            }
            constructor.setAccessible(true);
            return constructor.newInstance(objects);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            if (DEBUGGING) {
                System.out.println("deserialize : " + Arrays.toString(objects));
                System.out.println("deserialize : " + Arrays.toString(classes));
            }
        }
    }
}
