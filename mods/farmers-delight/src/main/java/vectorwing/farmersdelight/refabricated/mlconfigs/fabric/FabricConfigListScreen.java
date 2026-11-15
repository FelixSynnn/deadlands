package vectorwing.farmersdelight.refabricated.mlconfigs.fabric;

import java.net.URI;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent.OpenUrl;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import vectorwing.farmersdelight.refabricated.mlconfigs.ModConfigHolder;

public class FabricConfigListScreen extends Screen {
    protected final Screen parent;
    protected final ModConfigHolder[] configs;
    protected final @Nullable Identifier background;
    private final ItemStack mainIcon;
    private final String modId;
    private final String modURL;
    protected FabricConfigListScreen.ConfigList list;

    public FabricConfigListScreen(
        String modId, ItemStack mainIcon, Component displayName, @Nullable Identifier background, Screen parent, ModConfigHolder... specs
    ) {
        super(displayName);
        this.parent = parent;
        this.configs = specs;
        this.background = background;
        this.mainIcon = mainIcon;
        this.modId = modId;
        this.modURL = (String)((ModContainer)FabricLoader.getInstance().getModContainer(modId).get()).getMetadata().getContact().get("homepage").orElse(null);
    }

    @Override
    protected void init() {
        this.list = new FabricConfigListScreen.ConfigList(this.minecraft, this.width, this.height, 32, 40, this.configs);
        this.addRenderableWidget(this.list);
        this.addExtraButtons();
    }

    protected void addExtraButtons() {
        this.addRenderableWidget(
            Button.builder(CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 - 155 + 160, this.height - 29, 150, 20)
                .build()
        );
    }

    @Override
    public void removed() {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
        if (this.modURL != null && this.isMouseWithin(this.width / 2 - 90, 8, 180, 18, mouseX, mouseY)) {
            graphics.drawWordWrap(this.font, Component.translatable("gui.farmersdelight.open_mod_page", this.modId), mouseX, mouseY, 200, -1);
        }

        int titleWidth = this.font.width(this.title) + 35;
        graphics.renderItem(this.mainIcon, this.width / 2 + titleWidth / 2 - 17, 10);
        graphics.renderItem(this.mainIcon, this.width / 2 - titleWidth / 2, 10);
    }

    private boolean isMouseWithin(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (this.modURL != null && this.isMouseWithin(this.width / 2 - 90, 8, 180, 18, (int)event.x(), (int)event.y())) {
            defaultHandleGameClickEvent(new OpenUrl(URI.create(this.modURL)), this.minecraft, this);
            return true;
        } else {
            return super.mouseClicked(event, isDoubleClick);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    protected class ConfigButton extends Entry<FabricConfigListScreen.ConfigButton> {
        private final List<AbstractWidget> children;

        private ConfigButton(AbstractWidget widget) {
            this.children = List.of(widget);
        }

        protected ConfigButton(ModConfigHolder spec, int width, int buttonWidth) {
            this(
                Button.builder(
                        Component.literal(spec.getFileName()),
                        b -> Minecraft.getInstance().setScreen(spec.makeScreen(FabricConfigListScreen.this, FabricConfigListScreen.this.background))
                    )
                    .bounds(width / 2 - buttonWidth / 2, 0, buttonWidth, 20)
                    .build()
            );
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            this.children.forEach(button -> {
                button.setY(this.getContentY());
                button.render(graphics, mouseX, mouseY, partialTick);
            });
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.children;
        }
    }

    protected class ConfigList extends ContainerObjectSelectionList<FabricConfigListScreen.ConfigButton> {
        public ConfigList(Minecraft minecraft, int width, int height, int y0, int itemHeight, ModConfigHolder... specs) {
            super(minecraft, width, height, y0, itemHeight);
            this.centerListVertically = true;

            for (ModConfigHolder s : specs) {
                this.addEntry(FabricConfigListScreen.this.new ConfigButton(s, this.width, this.getRowWidth()));
            }
        }

        @Override
        public int getRowWidth() {
            return 260;
        }

        @Override
        protected int scrollBarX() {
            return super.scrollBarX() + 32;
        }
    }
}
