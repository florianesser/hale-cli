package to.wetransform.halecli.groovy

import java.util.List

import org.codehaus.groovy.tools.shell.AnsiDetector;
import org.codehaus.groovy.tools.shell.Groovysh
import org.codehaus.groovy.tools.shell.IO
import org.codehaus.groovy.tools.shell.Main;
import org.codehaus.groovy.tools.shell.util.HelpFormatter
import org.codehaus.groovy.tools.shell.util.Logger
import org.codehaus.groovy.tools.shell.util.MessageSource
import org.codehaus.groovy.tools.shell.util.NoExitSecurityManager
import org.eclipse.core.runtime.IConfigurationElement
import org.eclipse.core.runtime.Platform;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole

import eu.esdihumboldt.cst.functions.groovy.GroovyConstants
import eu.esdihumboldt.cst.functions.groovy.helper.HelperFunctions
import groovy.transform.CompileStatic;
import to.wetransform.halecli.Command
import to.wetransform.halecli.CommandContext

class GroovyShellCommand implements Command {
  
  private final MessageSource messages = new MessageSource(Main)

  @Override
  public int run(List<String> args, CommandContext context) {
    IO io = new IO()
    Logger.io = io

    CliBuilder cli = new CliBuilder(usage : "${context.baseCommand} [options] [...]",
    formatter: new HelpFormatter(), writer: io.out)

    cli.h(longOpt: 'help', messages['cli.option.help.description'])
    cli.V(longOpt: 'version', messages['cli.option.version.description'])
    cli.v(longOpt: 'verbose', messages['cli.option.verbose.description'])
    cli.q(longOpt: 'quiet', messages['cli.option.quiet.description'])
    cli.d(longOpt: 'debug', messages['cli.option.debug.description'])
    cli.C(longOpt: 'color', args: 1, argName: 'FLAG', optionalArg: true, messages['cli.option.color.description'])
    cli.D(longOpt: 'define', args: 1, argName: 'NAME=VALUE', messages['cli.option.define.description'])
    cli.T(longOpt: 'terminal', args: 1, argName: 'TYPE', messages['cli.option.terminal.description'])

    OptionAccessor options = cli.parse(args)

    if (options.h) {
      cli.usage()
      return 0
    }

    if (options.V) {
      io.out.println(messages.format('cli.info.version', GroovySystem.version))
      System.exit(0)
    }

    if (options.hasOption('T')) {
      def type = options.getOptionValue('T')
      Main.setTerminalType(type)
    }

    if (options.hasOption('D')) {
      def values = options.getOptionValues('D')

      values.each { Main.setSystemProperty(it as String) }
    }

    if (options.v) {
      io.verbosity = IO.Verbosity.VERBOSE
    }

    if (options.d) {
      io.verbosity = IO.Verbosity.DEBUG
    }

    if (options.q) {
      io.verbosity = IO.Verbosity.QUIET
    }

    if (options.hasOption('C')) {
      def value = options.getOptionValue('C')
      Main.setColor(value)
    }

    int code

    // Groovy shell binding
    Binding binding = new Binding()
    populateBinding(binding)
    
    // Boot up the shell
    final Groovysh shell = new Groovysh(binding, io)
    
    // add imports
    shell.imports.addAll(determineImports())
    
    // Add a hook to display some status when shutting down...
    addShutdownHook {
      //
      // FIXME: We need to configure JLine to catch CTRL-C for us... Use gshell-io's InputPipe
      //

      if (code == null) {
        // Give the user a warning when the JVM shutdown abnormally, normal shutdown
        // will set an exit code through the proper channels

        println('WARNING: Abnormal JVM shutdown detected')
      }

      if (shell.history) {
        shell.history.flush()
      }
    }

    SecurityManager psm = System.getSecurityManager()
    System.setSecurityManager(new NoExitSecurityManager())

    try {
      code = shell.run(options.arguments() as String[])
    }
    finally {
      System.setSecurityManager(psm)
    }

    return code
  }
  
  void populateBinding(Binding binding) {
    // helper functions
    binding.setVariable(GroovyConstants.BINDING_HELPER_FUNCTIONS, HelperFunctions.createDefault())
  }
  
  @CompileStatic
  List<String> determineImports() {
    List<String> result = []
    
    // determine imports from extension (see DefaultGroovyService class)
    for (IConfigurationElement conf : Platform.getExtensionRegistry()
        .getConfigurationElementsFor('eu.esdihumboldt.util.groovy.sandbox')) {
      if (conf.getName().equals("import")) {
        String className = conf.getAttribute("class");
        String alias = conf.getAttribute("alias");
  
        if (className != null && !className.isEmpty()) {
          if (alias == null || alias.isEmpty()) {
            result << className
          }
          else {
            result << ("$className as $alias" as String)
          }
        }
      }
  
      // TODO support also other kind of imports?
      // e.g. star imports, static imports...
    }
        
    result
  }

  @Override
  public String getShortDescription() {
    'Launch a Groovy shell (for advanced users)'
  }

}