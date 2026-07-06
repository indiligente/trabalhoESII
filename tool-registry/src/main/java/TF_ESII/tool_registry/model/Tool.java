package TF_ESII.tool_registry.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tools")
public class Tool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    // JSON schema string describing the input parameters
    @Column(columnDefinition = "TEXT")
    private String parametersSchema;

    // Whether this tool is built-in (cannot be deleted)
    private boolean builtIn = false;

    public Tool() {}

    public Tool(String name, String description, String parametersSchema, boolean builtIn) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
        this.builtIn = builtIn;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getParametersSchema() { return parametersSchema; }
    public void setParametersSchema(String parametersSchema) { this.parametersSchema = parametersSchema; }
    public boolean isBuiltIn() { return builtIn; }
    public void setBuiltIn(boolean builtIn) { this.builtIn = builtIn; }
}
