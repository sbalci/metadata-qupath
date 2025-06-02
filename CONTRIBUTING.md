# Contributing to QuPath Cohort Metadata Extractor

Thank you for your interest in contributing to the QuPath Cohort Metadata Extractor! This project exists to serve the digital pathology community, and community contributions are essential to its success.

## 🎯 Ways to Contribute

### 🐛 Bug Reports
Found a bug? Please help us fix it by:
- Checking if the issue already exists in [GitHub Issues](https://github.com/sbalci/qupath-cohort-extractor/issues)
- Using our [bug report template](.github/ISSUE_TEMPLATE/bug_report.md)
- Providing detailed reproduction steps and error messages

### 💡 Feature Requests
Have an idea for improvement? We'd love to hear it:
- Use our [feature request template](.github/ISSUE_TEMPLATE/feature_request.md)
- Describe your use case and how it would benefit the community
- Consider whether it applies to specific scanners or is universally applicable

### 📝 Documentation
Help make the project more accessible:
- Improve README.md clarity
- Add usage examples for different scanners
- Translate documentation to other languages
- Create tutorial videos or blog posts

### 💻 Code Contributions
Enhance the codebase:
- Add support for new scanner types
- Improve metadata extraction algorithms
- Optimize performance for large datasets
- Add new quality control metrics

## 🚀 Getting Started

### Prerequisites
- QuPath 0.6.0+ installed and working
- Basic understanding of Groovy scripting
- Access to sample WSI files for testing
- Git and GitHub account

### Development Setup

1. **Fork and clone the repository**:
   ```bash
   git clone https://github.com/sbalci/metadata-qupath.git
   cd qupath-cohort-extractor
   ```

2. **Set up your development environment**:
   - Install QuPath 0.6.0+
   - Copy scripts to your QuPath scripts directory
   - Create a test project with sample images

3. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

### Testing Your Changes

Before submitting changes, please test with:
- **Multiple scanner types** (if available)
- **Different image formats** (SVS, TIFF, etc.)
- **Various image sizes** (small and large files)
- **Edge cases** (corrupted files, missing metadata)

### Testing Checklist
- [ ] Script runs without errors in QuPath
- [ ] Output CSV contains expected columns
- [ ] Metadata extraction works for your target scanner/format
- [ ] Processing log shows appropriate messages
- [ ] Performance is acceptable for large projects
- [ ] Changes don't break existing functionality

## 📋 Contribution Guidelines

### Code Style
- **Follow existing conventions** in the codebase
- **Comment your code** thoroughly, especially complex algorithms
- **Use descriptive variable names** that are clear to pathologists
- **Handle errors gracefully** with appropriate logging

### Commit Messages
Write clear, descriptive commit messages:
```
feat: add support for Hamamatsu NanoZoomer metadata
fix: resolve pixel size extraction for Aperio CS2 
docs: update installation instructions for Linux
refactor: improve error handling in metadata extraction
```

### Pull Request Process

1. **Update documentation** if needed (README, CHANGELOG)
2. **Add tests** for new functionality when possible
3. **Ensure all tests pass** and no errors in QuPath console
4. **Update CHANGELOG.md** with your changes
5. **Create a descriptive pull request** explaining:
   - What changes you made
   - Why you made them  
   - How to test them
   - Any potential impacts

### Pull Request Template
When creating a PR, please include:

```markdown
## Description
Brief description of changes made.

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Refactoring

## Scanner/Format Support
- [ ] Tested with Aperio
- [ ] Tested with Hamamatsu
- [ ] Tested with Ventana
- [ ] Tested with other: ___________

## Testing
- [ ] Tested with small images (<100MB)
- [ ] Tested with large images (>1GB)
- [ ] Tested with multiple file formats
- [ ] No errors in QuPath console
- [ ] Output CSV validates correctly

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
```

## 🏥 Pathologist Contributors

### Non-Coding Contributions
You don't need to be a programmer to contribute:
- **Testing with your images**: Help validate the tool with real clinical data
- **Documentation feedback**: Improve instructions from a user perspective
- **Feature suggestions**: Propose improvements based on clinical workflow needs
- **Bug reports**: Report issues you encounter in your work

### Clinical Validation
Help us ensure the tool works in real-world scenarios:
- Test with diverse tissue types and staining protocols
- Validate metadata accuracy against known image parameters
- Report discrepancies in scanner metadata interpretation
- Suggest clinical workflow improvements

## 🔬 Scanner-Specific Contributions

### Adding New Scanner Support
To add support for a new scanner:

1. **Gather sample files** and metadata examples
2. **Analyze the metadata structure** (use QuPath's Image Information dialog)
3. **Map metadata fields** to our standard schema
4. **Add extraction logic** in the appropriate section
5. **Test thoroughly** with multiple samples
6. **Document the mapping** for future reference

### Metadata Field Mapping
When adding new fields, consider:
- **Standardization**: Use consistent naming across scanners
- **Clinical relevance**: Focus on fields useful for analysis
- **Compatibility**: Ensure fields work across different software versions

## 🤝 Community Guidelines

### Code of Conduct
- **Be respectful** and constructive in all interactions
- **Focus on the science** and clinical applications
- **Help newcomers** understand digital pathology concepts
- **Share knowledge** freely within the community

### Communication
- **Use GitHub Issues** for bug reports and feature requests
- **Join discussions** to share ideas and experiences
- **Be patient** - this is a volunteer project with contributors worldwide
- **Provide context** about your clinical/research use case

## 📚 Resources

### Learning Resources
- [QuPath Documentation](https://qupath.readthedocs.io/)
- [Groovy Language Documentation](https://groovy-lang.org/documentation.html)
- [Digital Pathology Fundamentals](https://digitalpathologyassociation.org/)

### Sample Data
- [QuPath Sample Images](https://qupath.readthedocs.io/en/stable/docs/intro/acknowledgments.html#sample-images)
- [OpenSlide Test Data](https://openslide.org/demo/)

### Related Projects
- [QuPath](https://github.com/qupath/qupath)
- [OpenSlide](https://github.com/openslide/openslide)
- [Bio-Formats](https://github.com/ome/bioformats)

## 🎉 Recognition

Contributors will be recognized in:
- **CHANGELOG.md** for each release
- **README.md** acknowledgments section
- **GitHub contributors** list
- **Academic citations** when appropriate

## ❓ Questions?

If you have questions about contributing:
- **Check existing issues** and discussions
- **Create a new discussion** for general questions
- **Reach out** through GitHub Issues for specific problems

---

**Thank you for helping make digital pathology more accessible and standardized! 🙏**

*Every contribution, no matter how small, makes a difference to the community.*