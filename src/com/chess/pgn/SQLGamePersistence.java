package com.chess.pgn;

import com.chess.engine.board.Board;
import com.chess.engine.board.Move;
import com.chess.engine.player.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.*;


public class SQLGamePersistence implements PGNPersistence {

    private final Connection dbConnection;

    private static SQLGamePersistence INSTANCE = new SQLGamePersistence();

    private static final String NEXT_BEST_MOVE_QUERY =
            "SELECT SUBSTR(g1.moves, LENGTH('%s') + %d, INSTR(SUBSTR(g1.moves, LENGTH('%s') + %d, LENGTH(g1.moves)), ',') - 1), " +
                    "COUNT(*) FROM game g1 WHERE g1.moves LIKE '%s%%' AND (outcome = '%s') GROUP BY substr(g1.moves, LENGTH('%s') + %d, " +
                    "INSTR(substr(g1.moves, LENGTH('%s') + %d, LENGTH(g1.moves)), ',') - 1) ORDER BY 2 DESC";


    private SQLGamePersistence() {
        this.dbConnection = createDBConnection();
        createGameTable();
        createIndex("outcome", "OutcomeIndex");
        createIndex("moves", "MoveIndex");
//        createOutcomeIndex();
//        createMovesIndex();
    }

    private static Connection createDBConnection() {
        Connection c = null;
        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:new_chess.db");
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        System.out.println("Opened database successfully");
        return c;
    }

    public static SQLGamePersistence get() {
        return INSTANCE;
    }

    @Override
    public void persistGame(final Game game) {
        executePersist(game);
    }

    @Override
    public Move getNextBestMove(final Board board,
                                final Player player,
                                final String gameText) {
        return queryBestMove(board, player, gameText);
    }

    private Move queryBestMove(final Board board,
                               final Player player,
                               final String gameText) {

        String bestMove = "";
        String count = "0";
        try {
            final int offSet = gameText.isEmpty() ? 1 : 3;
            final String sqlString = String.format(NEXT_BEST_MOVE_QUERY, gameText, offSet, gameText, offSet, gameText,
                    player.getAlliance().name(), gameText, offSet, gameText, offSet);
            System.out.println(sqlString);
            final Statement gameStatement = this.dbConnection.createStatement();
            gameStatement.execute(sqlString);
            final ResultSet rs2 = gameStatement.getResultSet();
            if(rs2.next()) {
                bestMove = rs2.getString(1);
                count = rs2.getString(2);
            }
            gameStatement.close();
        }
        catch (final SQLException e) {
            e.printStackTrace();
        }
        System.out.println("\tselected book move = " +bestMove+ " with " +count+ " hits");
        return PGNUtilities.createMove(board, bestMove);
    }

    private void createGameTable() {
        Statement stmt = null;
        try {
            stmt = createDBConnection().createStatement();
            String sql = "CREATE TABLE IF NOT EXISTS GAME " +
                    "(ID INT PRIMARY KEY NOT NULL," +
                    " OUTCOME CHAR(10), " +
                    " MOVES CHAR(3072))";

            stmt.executeUpdate(sql);
            stmt.close();
            createDBConnection().close();
        } catch ( Exception e ) {
            System.err.println( e.getClass().getName() + ": " + e.getMessage() );
            System.exit(0);
        }
        System.out.println("Table created successfully");
    }

    private void createIndex(final String columnName,
                             final String indexName) {
        try {
            final String sqlString = "SELECT * FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_CATALOG = 'def' AND " +
                    "                 TABLE_SCHEMA = DATABASE() AND TABLE_NAME = \"game\" AND INDEX_NAME = \"" +indexName+"\"";
            final Statement gameStatement = this.dbConnection.createStatement();
            gameStatement.execute(sqlString);
            final ResultSet resultSet = gameStatement.getResultSet();
            if(!resultSet.isBeforeFirst() ) {
                final Statement indexStatement = this.dbConnection.createStatement();
                indexStatement.execute("CREATE INDEX " +indexName+ " on Game(" +columnName+ ");\n");
                indexStatement.close();
            }
            gameStatement.close();
        }
        catch (final SQLException e) {
            System.out.println("CREATE INDEX " +indexName+ " on Game(" +columnName+ ");\n");
            e.printStackTrace();
        }

    }

    private void createOutcomeIndex() {
        try {
            final String sqlString = "SELECT * FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_CATALOG = 'def' AND TABLE_SCHEMA = DATABASE() AND TABLE_NAME = \"game\" AND INDEX_NAME = \"OutcomeIndex\"";
            final Statement gameStatement = this.dbConnection.createStatement();
            gameStatement.execute(sqlString);
            final ResultSet resultSet = gameStatement.getResultSet();
            if(!resultSet.isBeforeFirst() ) {
                final Statement indexStatement = this.dbConnection.createStatement();
                indexStatement.execute("CREATE INDEX OutcomeIndex on Game(outcome);\n");
                indexStatement.close();
            }
            gameStatement.close();
        }
        catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    private void createMovesIndex() {
        try {
            final String sqlString = "SELECT * FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_CATALOG = 'def' AND TABLE_SCHEMA = DATABASE() AND TABLE_NAME = \"game\" AND INDEX_NAME = \"MoveIndex\"";
            final Statement gameStatement = this.dbConnection.createStatement();
            gameStatement.execute(sqlString);
            final ResultSet resultSet = gameStatement.getResultSet();
            if(!resultSet.isBeforeFirst() ) {
                final Statement indexStatement = this.dbConnection.createStatement();
                indexStatement.execute("CREATE INDEX MoveIndex on Game(moves);\n");
                indexStatement.close();
            }
            gameStatement.close();
        }
        catch (final SQLException e) {
            e.printStackTrace();
        }
    }

    public int getMaxGameRow() {
        int maxId = 0;
        try {
            final String sqlString = "SELECT MAX(ID) FROM Game";
            final Statement gameStatement = this.dbConnection.createStatement();
            gameStatement.execute(sqlString);
            final ResultSet rs2 = gameStatement.getResultSet();
            if(rs2.next()) {
                maxId = rs2.getInt(1);
            }
            gameStatement.close();
        }
        catch (final SQLException e) {
            e.printStackTrace();
        }
        return maxId;
    }

    private void executePersist(final Game game) {
        try {
            final String gameSqlString = "INSERT INTO Game(id, outcome, moves) VALUES(?, ?, ?);";
            final PreparedStatement gameStatement = this.dbConnection.prepareStatement(gameSqlString);
            gameStatement.setInt(1, getMaxGameRow() + 1);
            gameStatement.setString(2, game.getWinner());
            gameStatement.setString(3, game.getMoves().toString().replaceAll("\\[", "").replaceAll("\\]", ""));
            gameStatement.executeUpdate();
            gameStatement.close();
        }
        catch (final SQLException e) {
            e.printStackTrace();
        }
    }

}
