package com.capco.brsp.synthesisengine.shell;

import lombok.RequiredArgsConstructor;
import org.springframework.shell.component.view.TerminalUI;
import org.springframework.shell.component.view.TerminalUIBuilder;
import org.springframework.shell.component.view.control.BoxView;
import org.springframework.shell.geom.HorizontalAlign;
import org.springframework.shell.geom.VerticalAlign;

@RequiredArgsConstructor
public class ShellViews {
    private final TerminalUIBuilder builder;

    void sample() {
        TerminalUI ui = builder.build();
        BoxView view = new BoxView();
        ui.configure(view);
        view.setDrawFunction((screen, rect) -> {
            screen.writerBuilder()
                    .build()
                    .text("Hello World", rect, HorizontalAlign.CENTER, VerticalAlign.CENTER);
            return rect;
        });
        ui.setRoot(view, true);
        ui.run();
    }
}
