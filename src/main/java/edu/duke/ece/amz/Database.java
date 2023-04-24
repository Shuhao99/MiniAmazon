package edu.duke.ece.amz;
import edu.duke.ece.amz.proto.WorldAmazon.*;
import jdk.internal.net.http.common.Pair;

import java.sql.*;
import java.util.*;


public class Database {
    // table name
    private static final String TABLE_ITEM = "item";
    private static final String TABLE_ORDER = "order";
    private static final String TABLE_ORDER_ITEMS = "order_items";
    private static final String TABLE_WAREHOUSE = "warehouse";

    // database configuration
    private static final String dbUrl = "jdbc:postgresql://localhost:5432/amazon_db";
    private static final String dbUser = "postgres";
    private static final String dbPassword = "postgres";

    // warehouse list
    private final Map<Integer, Pair<Integer, Integer>> warehouseMap;

    public Database(){
        Map<Integer, Pair<Integer, Integer>> tempMap = new HashMap<>();
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();

            ResultSet result = statement.executeQuery(
                    String.format("SELECT * FROM %s;", TABLE_WAREHOUSE)
            );

            while (result.next()){
                tempMap.put(
                        result.getInt("id"),
                        new Pair<>(
                                result.getInt("x_cord"),
                                result.getInt("y_cord")
                        )
                );
            }

            statement.close();
            conn.close();

        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.toString());
            tempMap = null;
        }

        warehouseMap = tempMap;
    }

    // Get warehouse list
    public List<AInitWarehouse> getWhList() {
        List<AInitWarehouse> whList = new ArrayList<>();
        for (int key : warehouseMap.keySet()){
            whList.add(
                    AInitWarehouse.newBuilder().
                            setId(key).
                            setX(warehouseMap.get(key).first).
                            setY(warehouseMap.get(key).second).
                            build()
            );
        }
        return whList;
    }

    /**
     * Get Warehouse details using package id
     * @param packageId (OrderId)
     * @return (whID, dest)
     * */
    public Pair<Integer, Pair<Integer, Integer>> getOrderInfo(long packageId){
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();

            ResultSet result = statement.executeQuery(
                    String.format("SELECT * FROM %s" +
                            "WHERE order_id = %d;",
                            TABLE_ORDER, packageId)
            );

            int whID = -1;
            Pair<Integer, Integer> dest = new Pair<>(-1, -1);
            if(result.next()){
                whID = result.getInt("warehouse");
                dest = new Pair<>(
                        result.getInt("dest_x"),
                        result.getInt("dest_y")
                );
            }
            statement.close();
            conn.close();
            return new Pair<>(whID, dest);

        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.toString());
            return null;
        }
    }

    /**
     * get all products in a package
     * @param packageId (OrderID)
     * @return list of products
     */
    public List<AProduct> getProducts(long packageId) {
        List<AProduct> res;
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();

            ResultSet result = statement.executeQuery(String.format(
                    "SELECT item.id, item.description, order.count " +
                            "FROM %s AS order, %s AS item" +
                            "WHERE order_id = %d AND order.item = item.id;",
                    TABLE_ORDER_ITEMS, TABLE_ITEM, packageId)
            );

            List<AProduct> products = new ArrayList<>();

            while (result.next()) {
                products.add(
                        AProduct.newBuilder().
                                setId(result.getInt("id")).
                                setDescription(result.getString("description")).
                                setCount(result.getInt("count")).
                                build()
                );
            }

            statement.close();
            conn.close();
            res = products;

        } catch (ClassNotFoundException | SQLException e) {
            System.err.println(e.toString());
            res = null;
        }
        return res;
    }


    /**
     * get the package instance of the order
     * @param packageId (OrderID)
     * @return package instance
     */
    public Package getPackage(long packageId){
        Pair<Integer, Pair<Integer, Integer>> orderInfo = getOrderInfo(packageId);
        int whId = orderInfo.first;
        int desX = orderInfo.second.first;
        int desY = orderInfo.second.second;

        List<AProduct> products = getProducts(packageId);
        Package pkg;
        pkg = new Package(
                packageId, whId, desX, desY,
                warehouseMap.get(whId).first,
                warehouseMap.get(whId).second,
                products
        );
        return pkg;
    }

    /**
     * Update package status
     * @param packageId:
     *                 OrderID
     * @param status:
     *              The status of package (String)
     * @return boolean value of execute status
     * */
    public boolean updateStatus(long packageId, String status){
        try {
            Class.forName("org.postgresql.Driver");
            Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
            conn.setAutoCommit(false);

            Statement statement = conn.createStatement();
            statement.executeUpdate(String.format(
                    "UPDATE %s SET status='%s' WHERE id=%d;",
                    TABLE_ORDER, status, packageId)
            );
            conn.commit();

            statement.close();
            conn.close();
            return true;
        }catch (Exception e){
            System.err.println(e.toString());
        }
        return false;
    }

}
