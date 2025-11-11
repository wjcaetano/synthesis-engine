package com.capco.brsp.synthesisengine.utils;

import io.proleap.cobol.asg.metamodel.procedure.Statement;
import io.proleap.cobol.asg.metamodel.procedure.close.CloseFile;
import io.proleap.cobol.asg.metamodel.procedure.close.CloseStatement;
import io.proleap.cobol.asg.metamodel.procedure.evaluate.impl.EvaluateStatementImpl;
import io.proleap.cobol.asg.metamodel.procedure.ifstmt.IfStatement;
import io.proleap.cobol.asg.metamodel.procedure.open.InputPhrase;
import io.proleap.cobol.asg.metamodel.procedure.open.OpenStatement;
import io.proleap.cobol.asg.metamodel.procedure.open.OutputPhrase;

import java.util.ArrayList;
import java.util.List;

public class COBOLUtils {
    private static final COBOLUtils INSTANCE = new COBOLUtils();

    private COBOLUtils() {
    }

    public static COBOLUtils getInstance() {
        return INSTANCE;
    }

    public static List<Object> getStatements(List<Statement> statements) {
        List<Object> statementsList = new ArrayList<>();
        statements.forEach(p -> {
            if (p.getStatementType().toString().equals("CALL")) {
                statementsList.add(p);
            }
            if (p.getStatementType().toString().equals("PERFORM")) {
                statementsList.add(p);
            }
            if (p.getStatementType().toString().equals("WRITE")) {
                statementsList.add(p);
            }
            if (p.getStatementType().toString().equals("READ")) {
                statementsList.add(p);
            }
            if (p.getStatementType().toString().equals("CLOSE")) {
                CloseStatement closeStatement = (CloseStatement) p;
                List<CloseFile> closeFiles = closeStatement.getCloseFiles();
                closeFiles.forEach(q -> statementsList.add(q.getFileCall()));
            }
            if (p.getStatementType().toString().equals("OPEN")) {
                OpenStatement openStatement = (OpenStatement) p;
                List<InputPhrase> inputPhrases = openStatement.getInputPhrases();
                inputPhrases.forEach(q -> statementsList.addAll(q.getInputs()));

                List<OutputPhrase> outputPhrases = openStatement.getOutputPhrases();
                outputPhrases.forEach(q -> statementsList.addAll(q.getOutputs()));
            }
            if (p.getStatementType().toString().equals("IF")) {
                IfStatement ifStatement = (IfStatement) p;
                if (ifStatement.getThen() != null)
                    statementsList.addAll(getStatements(ifStatement.getThen().getStatements()));
                if (ifStatement.getElse() != null)
                    statementsList.addAll(getStatements(ifStatement.getElse().getStatements()));
            }
            if (p.getStatementType().toString().equals("EVALUATE")) {
                EvaluateStatementImpl evaluateStatementImpl = (EvaluateStatementImpl) p;
                evaluateStatementImpl.getWhenPhrases().forEach(q -> {
                    if (q.getStatements() != null)
                        statementsList.addAll(getStatements(q.getStatements()));
                });
                if (evaluateStatementImpl.getWhenOther() != null) {
                    statementsList.addAll(getStatements(evaluateStatementImpl.getWhenOther().getStatements()));
                }
            }

        });

        return statementsList;
    }
}
